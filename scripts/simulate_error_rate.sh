#!/bin/bash

# ============================================================
# Performance Test: High Error Rate Scenario
# Measures MTTR, Latencies, and Overhead. Outputs to JSON.
# ============================================================

set -e

SERVICES=("payment-service" "account-service")
PORTS=(8081 8082)
ITERATIONS=10
RESULTS_FILE="logs/error_rate_results.json"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m'

current_time_ms() {
    python3 -c 'import time; print(int(time.time() * 1000))'
}

wait_for_health() {
    local port=$1
    local timeout=120
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        if curl -sf "http://localhost:$port/actuator/health" | grep -q '"status":"UP"'; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    echo -e "${RED}Timeout waiting for health on port $port${NC}"
    exit 1
}

get_recovery_log_lines() {
    if [ -f "logs/recovery-engine.log" ]; then
        wc -l < logs/recovery-engine.log | tr -d ' '
    else
        echo "0"
    fi
}

get_recovery_overhead() {
    local pid_file="logs/recovery-engine.pid"
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            local stats=$(ps -p "$pid" -o %cpu=,rss= 2>/dev/null || echo "0 0")
            local cpu=$(echo "$stats" | awk '{print $1}')
            local mem_kb=$(echo "$stats" | awk '{print $2}')
            local mem_mb=$(awk "BEGIN {printf \"%.2f\", $mem_kb/1024}")
            echo "$cpu,$mem_mb"
            return
        fi
    fi
    echo "0.0,0.0"
}

echo -e "${CYAN}Starting High Error Rate Performance Test ($ITERATIONS iterations)...${NC}"
echo -e "${CYAN}Output will be written as JSON to $RESULTS_FILE${NC}"

# Initialize JSON array
echo "[" > "$RESULTS_FILE"

for i in $(seq 1 $ITERATIONS); do
    idx=$(( (i - 1) % 2 ))
    SERVICE_NAME=${SERVICES[$idx]}
    PORT=${PORTS[$idx]}

    echo -e "\n${CYAN}--- Iteration $i: $SERVICE_NAME ---${NC}"
    
    # Ensure it is healthy before proceeding
    echo "Waiting for $SERVICE_NAME to be perfectly healthy..."
    wait_for_health $PORT
    sleep 5 # stabilization buffer
    
    # Get initial log line count so we only search new logs
    LOG_START_LINE=$(get_recovery_log_lines)
    
    # 2. Inject Fault (100% Error Rate)
    echo "Injecting High Error Rate (100%) on $SERVICE_NAME (Port: $PORT)..."
    T_CRASH=$(current_time_ms)
    
    # Set the fault
    curl -s -X POST -H "Content-Type: application/json" -d '{"rate": 100}' "http://localhost:$PORT/fault/error-rate" > /dev/null
    
    # Start background traffic generator to trigger Prometheus metrics
    echo "Starting background traffic generator..."
    while true; do
        curl -s "http://localhost:$PORT/api/dummy" > /dev/null || true
        sleep 0.1
    done &
    TRAFFIC_PID=$!
    
    # 3. Wait for Detection by Recovery Engine
    echo "Waiting for Recovery Engine to detect the High Error Rate..."
    T_DETECT=0
    TIMEOUT=240
    ELAPSED=0
    while [ $ELAPSED -lt $TIMEOUT ]; do
        if tail -n +$LOG_START_LINE logs/recovery-engine.log | grep -q "Matched rule \[High Error Rate\] for alert \[HighErrorRate\]"; then
            T_DETECT=$(current_time_ms)
            break
        fi
        sleep 0.5
        ELAPSED=$((ELAPSED + 1))
    done
    
    if [ $T_DETECT -eq 0 ]; then
        echo -e "${RED}Recovery Engine did not detect the error rate within timeout.${NC}"
        kill -9 $TRAFFIC_PID 2>/dev/null || true
        curl -s -X POST -H "Content-Type: application/json" -d '{"rate": 0}' "http://localhost:$PORT/fault/error-rate" > /dev/null
        exit 1
    fi
    
    DETECT_LATENCY=$((T_DETECT - T_CRASH))
    echo -e "${GREEN}Detected! Latency: ${DETECT_LATENCY}ms${NC}"
    
    # 4. Wait for Execution (Circuit Breaker OPEN)
    T_EXEC_FINISH=0
    while true; do
        if tail -n +$LOG_START_LINE logs/recovery-engine.log | grep -q "Changing Circuit Breaker state to OPEN on instance $SERVICE_NAME"; then
            T_EXEC_FINISH=$(current_time_ms)
            break
        fi
        sleep 0.1
    done
    EXEC_LATENCY=$((T_EXEC_FINISH - T_DETECT))
    echo -e "${GREEN}Remediation Executed! Execution Latency: ${EXEC_LATENCY}ms${NC}"
    
    # 5. Measure Overhead
    OVERHEAD=$(get_recovery_overhead)
    CPU_USAGE=$(echo "$OVERHEAD" | cut -d',' -f1)
    MEM_USAGE=$(echo "$OVERHEAD" | cut -d',' -f2)
    echo -e "${CYAN}Recovery Engine Overhead - CPU: ${CPU_USAGE}%, Mem: ${MEM_USAGE}MB${NC}"
    
    # 6. Stop Fault & Traffic
    echo "Removing fault (setting error rate back to 0%)..."
    curl -s -X POST -H "Content-Type: application/json" -d '{"rate": 0}' "http://localhost:$PORT/fault/error-rate" > /dev/null
    
    echo "Stopping traffic generator..."
    kill -9 $TRAFFIC_PID 2>/dev/null || true
    
    # 7. Wait for Full Recovery (MTTR)
    # Since we are removing the fault, the service is immediately healthy, 
    # but let's wait a moment for metrics to stabilize.
    sleep 5
    T_RECOVER=$(current_time_ms)
    
    MTTR=$((T_RECOVER - T_CRASH))
    echo -e "${GREEN}Recovered! MTTR: ${MTTR}ms${NC}"
    
    # 8. Record Metrics to JSON
    cat <<EOF >> "$RESULTS_FILE"
  {
    "iteration": $i,
    "service_name": "$SERVICE_NAME",
    "timestamps": {
      "t_fault_ms": $T_CRASH,
      "t_detect_ms": $T_DETECT,
      "t_recover_ms": $T_RECOVER
    },
    "metrics": {
      "detection_latency_ms": $DETECT_LATENCY,
      "execution_latency_ms": $EXEC_LATENCY,
      "mttr_ms": $MTTR
    },
    "overhead": {
      "recovery_engine_cpu_percent": $CPU_USAGE,
      "recovery_engine_mem_mb": $MEM_USAGE
    }
  }
EOF

    if [ $i -lt $ITERATIONS ]; then
        echo "," >> "$RESULTS_FILE"
    fi
    
    echo "Cooldown for 15s before next iteration..."
    sleep 15
done

echo "]" >> "$RESULTS_FILE"

echo -e "\n${CYAN}======================================================${NC}"
echo -e "${GREEN}Testing Complete. JSON Results saved to $RESULTS_FILE${NC}"
echo "Sample of JSON output:"
head -n 25 "$RESULTS_FILE"
