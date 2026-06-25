#!/bin/bash

# ============================================================
# Performance Test: Service Crash Scenario (Academic Edition)
# Measures MTTR, Latencies, and Overhead. Outputs to JSON.
# ============================================================

set -e

SERVICE_NAME="payment-service"
PORT=8081
ITERATIONS=10
RESULTS_FILE="logs/crash_scenario_results.json"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m'

current_time_ms() {
    date +%s%3N
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
            # ps output format: %cpu rss(KB)
            local stats=$(ps -p "$pid" -o %cpu=,rss= 2>/dev/null || echo "0 0")
            local cpu=$(echo "$stats" | awk '{print $1}')
            local mem_kb=$(echo "$stats" | awk '{print $2}')
            # Convert KB to MB
            local mem_mb=$(awk "BEGIN {printf \"%.2f\", $mem_kb/1024}")
            echo "$cpu,$mem_mb"
            return
        fi
    fi
    echo "0.0,0.0"
}

ensure_service_running() {
    local pid_file="logs/${SERVICE_NAME}.pid"
    if [ ! -f "$pid_file" ] || ! kill -0 $(cat "$pid_file" 2>/dev/null) 2>/dev/null; then
        echo -e "${YELLOW}$SERVICE_NAME is not running. Starting it now via debug_launch.sh...${NC}"
        ./scripts/debug_launch.sh payment > /dev/null 2>&1 &
        wait_for_health $PORT
        echo -e "${GREEN}$SERVICE_NAME is now up and running.${NC}"
        sleep 5
    fi
}

echo -e "${CYAN}Starting Service Crash Performance Test ($ITERATIONS iterations)...${NC}"
echo -e "${CYAN}Output will be written as JSON to $RESULTS_FILE${NC}"

# Initialize JSON array
echo "[" > "$RESULTS_FILE"

for i in $(seq 1 $ITERATIONS); do
    echo -e "\n${CYAN}--- Iteration $i ---${NC}"
    
    # 1. Pre-check: Ensure target service is running
    ensure_service_running
    
    # Ensure it is healthy before proceeding
    echo "Waiting for $SERVICE_NAME to be perfectly healthy..."
    wait_for_health $PORT
    sleep 5 # stabilization buffer
    
    # Get initial log line count so we only search new logs
    LOG_START_LINE=$(get_recovery_log_lines)
    
    # 2. Inject Crash
    PID_FILE="logs/${SERVICE_NAME}.pid"
    # Find the actual Java process bound to the port (Maven PID is not enough)
    TARGET_PID=$(lsof -i :$PORT -sTCP:LISTEN | awk 'NR>1 && $1 ~ /java/ {print $2}' | head -n 1)
    if [ -z "$TARGET_PID" ]; then
        echo -e "${RED}Could not find process listening on port $PORT. Is it running?${NC}"
        exit 1
    fi
    echo "Crashing $SERVICE_NAME (Port: $PORT, Java PID: $TARGET_PID)..."
    
    T_CRASH=$(current_time_ms)
    # Kill the actual Java process
    kill -9 "$TARGET_PID" 2>/dev/null || true
    # Also kill the Maven wrapper so debug_launch.sh can restart cleanly
    kill -9 $(cat "$PID_FILE" 2>/dev/null) 2>/dev/null || true
    
    # 3. Wait for Detection by Recovery Engine
    echo "Waiting for Recovery Engine to detect the crash..."
    T_DETECT=0
    # Timeout to prevent infinite loop (120 actual seconds)
    TIMEOUT=240
    ELAPSED=0
    while [ $ELAPSED -lt $TIMEOUT ]; do
        if tail -n +$LOG_START_LINE logs/recovery-engine.log | grep -q "Executing RESTART on service $SERVICE_NAME"; then
            T_DETECT=$(current_time_ms)
            break
        fi
        sleep 0.5
        ELAPSED=$((ELAPSED + 1))
    done
    
    if [ $T_DETECT -eq 0 ]; then
        echo -e "${RED}Recovery Engine did not detect the crash within timeout. Ensure Prometheus is scraping and alerting correctly.${NC}"
        exit 1
    fi
    
    DETECT_LATENCY=$((T_DETECT - T_CRASH))
    echo -e "${GREEN}Detected! Latency: ${DETECT_LATENCY}ms${NC}"
    
    # 4. Wait for Execution (since ActionInvoker takes ~500ms then finishes)
    T_EXEC_FINISH=0
    while true; do
        if tail -n +$LOG_START_LINE logs/recovery-engine.log | grep -q "Sending RESTART command to Spring Boot Admin for instance $SERVICE_NAME"; then
            T_EXEC_FINISH=$(current_time_ms)
            break
        fi
        sleep 0.1
    done
    EXEC_LATENCY=$((T_EXEC_FINISH - T_DETECT))
    echo -e "${GREEN}Remediation Executed! Execution Latency: ${EXEC_LATENCY}ms${NC}"
    
    # 5. Measure Overhead at Execution Time
    OVERHEAD=$(get_recovery_overhead)
    CPU_USAGE=$(echo "$OVERHEAD" | cut -d',' -f1)
    MEM_USAGE=$(echo "$OVERHEAD" | cut -d',' -f2)
    echo -e "${CYAN}Recovery Engine Overhead - CPU: ${CPU_USAGE}%, Mem: ${MEM_USAGE}MB${NC}"
    
    # 6. Close the loop: The Recovery Engine currently mocks the OS restart.
    echo "Triggering actual OS-level restart via debug_launch.sh..."
    ./scripts/debug_launch.sh payment > /dev/null 2>&1 &
    
    # 7. Wait for Full Recovery (MTTR)
    echo "Waiting for $SERVICE_NAME to become healthy again..."
    wait_for_health $PORT
    T_RECOVER=$(current_time_ms)
    
    MTTR=$((T_RECOVER - T_CRASH))
    echo -e "${GREEN}Recovered! MTTR: ${MTTR}ms${NC}"
    
    # 8. Record Metrics to JSON
    # Formatting JSON object
    cat <<EOF >> "$RESULTS_FILE"
  {
    "iteration": $i,
    "timestamps": {
      "t_crash_ms": $T_CRASH,
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

    # Add comma if not the last iteration
    if [ $i -lt $ITERATIONS ]; then
        echo "," >> "$RESULTS_FILE"
    fi
    
    echo "Cooldown for 15s before next iteration..."
    sleep 15
done

# Close JSON array
echo "]" >> "$RESULTS_FILE"

echo -e "\n${CYAN}======================================================${NC}"
echo -e "${GREEN}Testing Complete. JSON Results saved to $RESULTS_FILE${NC}"
echo "Sample of JSON output:"
head -n 25 "$RESULTS_FILE"
