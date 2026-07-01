#!/bin/bash

# ============================================================
# Performance Test: Memory Leak Scenario
# Measures MTTR, Latencies, and Overhead. Outputs to JSON.
# ============================================================

set -e

SERVICES=("payment-service" "account-service")
PORTS=(8081 8082)
PROMETHEUS_URL="http://localhost:9090"
ITERATIONS=10
RESULTS_FILE="logs/memory_leak_results.json"
TRAFFIC_PID=""
CURRENT_PORT=""

cleanup() {
    echo -e "\n${YELLOW}Cleaning up...${NC}"
    if [ -n "$TRAFFIC_PID" ] && kill -0 "$TRAFFIC_PID" 2>/dev/null; then
        kill -9 "$TRAFFIC_PID" 2>/dev/null || true
        wait "$TRAFFIC_PID" 2>/dev/null || true
    fi
    if [ -n "$CURRENT_PORT" ]; then
        curl -s -X POST -H "Content-Type: application/json" -d '{"enable": false}' "http://localhost:$CURRENT_PORT/fault/memory-leak" > /dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

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

# Query JVM heap usage from Prometheus for a given job name
# Returns heap usage as a human-readable percentage string, or "N/A" on failure
query_heap_usage() {
    local job_name=$1
    local result
    # Use sum() to aggregate across all heap pools, clamp_min to filter out pools where max=-1
    result=$(curl -sf --max-time 5 "${PROMETHEUS_URL}/api/v1/query" \
        --data-urlencode "query=sum(jvm_memory_used_bytes{job=\"${job_name}\",area=\"heap\"}) / clamp_min(sum(jvm_memory_max_bytes{job=\"${job_name}\",area=\"heap\"}), 1)" \
        2>/dev/null) || true

    if [ -n "$result" ] && echo "$result" | grep -q '"result"'; then
        local value
        value=$(echo "$result" | python3 -c "
import sys, json
r = json.load(sys.stdin)
results = r.get('data', {}).get('result', [])
if results:
    ratio = float(results[0]['value'][1])
    print(f'{ratio:.4f} ({ratio*100:.1f}%)')
else:
    print('N/A')
" 2>/dev/null)
        echo "${value:-N/A}"
    else
        echo "N/A"
    fi
}

echo -e "${CYAN}Starting Memory Leak Test ($ITERATIONS iterations)...${NC}"
echo -e "${CYAN}Output will be written as JSON to $RESULTS_FILE${NC}"

# Initialize JSON array
echo "[" > "$RESULTS_FILE"

for i in $(seq 1 $ITERATIONS); do
    idx=$(( (i - 1) % 2 ))
    SERVICE_NAME=${SERVICES[$idx]}
    PORT=${PORTS[$idx]}
    CURRENT_PORT=$PORT

    echo -e "\n${CYAN}--- Iteration $i: $SERVICE_NAME ---${NC}"

    # 1. Ensure it is healthy before proceeding
    echo "Waiting for $SERVICE_NAME to be perfectly healthy..."
    wait_for_health $PORT
    sleep 5 # stabilization buffer

    # Get initial log line count so we only search new logs
    LOG_START_LINE=$(get_recovery_log_lines)

    # Capture heap baseline from Prometheus
    HEAP_BASELINE=$(query_heap_usage "$SERVICE_NAME")
    echo -e "${CYAN}Heap baseline: ${HEAP_BASELINE}${NC}"

    # 2. Inject Fault (Memory Leak) — single call is sufficient
    echo "Injecting Memory Leak on $SERVICE_NAME (Port: $PORT)..."
    T_CRASH=$(current_time_ms)

    INJECT_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" \
        -d '{"enable": true, "maxMb": 450}' \
        "http://localhost:$PORT/fault/memory-leak" 2>/dev/null)

    # Verify injection was accepted
    if echo "$INJECT_RESPONSE" | grep -qi '"active"[[:space:]]*:[[:space:]]*true'; then
        echo -e "${GREEN}Fault injection confirmed: active${NC}"
    else
        echo -e "${RED}WARNING: Fault injection response unexpected: $INJECT_RESPONSE${NC}"
    fi

    # 3. Start background traffic generator with realistic endpoint mix
    echo "Starting background traffic generator..."
    (
        while true; do
            # Mix health checks with actuator endpoints for realistic GC pressure
            curl -s "http://localhost:$PORT/actuator/health" > /dev/null 2>&1
            curl -s "http://localhost:$PORT/actuator/metrics" > /dev/null 2>&1
            curl -s "http://localhost:$PORT/actuator/prometheus" > /dev/null 2>&1
            sleep 0.5
        done
    ) &
    TRAFFIC_PID=$!

    # 4. Wait for Detection by Recovery Engine
    echo "Waiting for Recovery Engine to detect the Memory Leak..."
    T_DETECT=0
    TIMEOUT=600
    ELAPSED=0
    LOG_TAIL_START=$((LOG_START_LINE + 1))
    while [ $ELAPSED -lt $TIMEOUT ]; do
        if tail -n +$LOG_TAIL_START logs/recovery-engine.log 2>/dev/null | grep -q "Matched rule \[Memory Leak\] for alert \[HighMemoryUsage\]"; then
            T_DETECT=$(current_time_ms)
            break
        fi
        sleep 1
        ELAPSED=$((ELAPSED + 1))
    done

    if [ $T_DETECT -eq 0 ]; then
        echo -e "${RED}Recovery Engine did not detect Memory Leak within timeout.${NC}"
        # cleanup trap handles background processes and fault disabling
        exit 1
    fi

    DETECT_LATENCY=$((T_DETECT - T_CRASH))
    echo -e "${GREEN}Detected! Latency: ${DETECT_LATENCY}ms${NC}"

    # Capture heap at detection time
    HEAP_AT_DETECTION=$(query_heap_usage "$SERVICE_NAME")
    echo -e "${CYAN}Heap at detection: ${HEAP_AT_DETECTION}${NC}"

    # 5. Wait for Execution (RESTART)
    T_EXEC_FINISH=0
    EXEC_TIMEOUT=120
    EXEC_ELAPSED=0
    while [ $EXEC_ELAPSED -lt $EXEC_TIMEOUT ]; do
        if tail -n +$LOG_TAIL_START logs/recovery-engine.log 2>/dev/null | grep -q "Sending RESTART command to Spring Boot Admin for instance $SERVICE_NAME"; then
            T_EXEC_FINISH=$(current_time_ms)
            break
        fi
        sleep 0.1
        EXEC_ELAPSED=$((EXEC_ELAPSED + 1))
    done

    if [ $T_EXEC_FINISH -eq 0 ]; then
        echo -e "${RED}Recovery Engine did not execute RESTART within ${EXEC_TIMEOUT}s timeout.${NC}"
        exit 1
    fi
    EXEC_LATENCY=$((T_EXEC_FINISH - T_DETECT))
    echo -e "${GREEN}Remediation Executed! Execution Latency: ${EXEC_LATENCY}ms${NC}"

    # 6. Measure Overhead
    OVERHEAD=$(get_recovery_overhead)
    CPU_USAGE=$(echo "$OVERHEAD" | cut -d',' -f1)
    MEM_USAGE=$(echo "$OVERHEAD" | cut -d',' -f2)
    echo -e "${CYAN}Recovery Engine Overhead - CPU: ${CPU_USAGE}%, Mem: ${MEM_USAGE}MB${NC}"

    # 7. Stop Traffic & Wait for recovery
    echo "Stopping traffic generator..."
    kill -9 $TRAFFIC_PID 2>/dev/null || true
    wait $TRAFFIC_PID 2>/dev/null || true
    TRAFFIC_PID=""

    echo "Waiting for service to become healthy again after restart..."
    wait_for_health $PORT
    T_RECOVER=$(current_time_ms)

    # Disable fault injection explicitly in case restart didn't clear memory leak completely
    # (Though a JVM restart would clear the leaked memory objects inherently)
    curl -s -X POST -H "Content-Type: application/json" -d '{"enable": false}' "http://localhost:$PORT/fault/memory-leak" > /dev/null || true

    MTTR=$((T_RECOVER - T_CRASH))
    echo -e "${GREEN}Recovered! MTTR: ${MTTR}ms${NC}"

    # 8. Record Metrics to JSON (comma handled inline)
    COMMA=""
    if [ $i -lt $ITERATIONS ]; then
        COMMA=","
    fi

    cat <<EOF >> "$RESULTS_FILE"
  {
    "iteration": $i,
    "service_name": "$SERVICE_NAME",
    "timestamps": {
      "t_fault_ms": $T_CRASH,
      "t_detect_ms": $T_DETECT,
      "t_exec_ms": $T_EXEC_FINISH,
      "t_recover_ms": $T_RECOVER
    },
    "metrics": {
      "detection_latency_ms": $DETECT_LATENCY,
      "execution_latency_ms": $EXEC_LATENCY,
      "mttr_ms": $MTTR
    },
    "heap": {
      "baseline": "$HEAP_BASELINE",
      "at_detection": "$HEAP_AT_DETECTION"
    },
    "overhead": {
      "recovery_engine_cpu_percent": $CPU_USAGE,
      "recovery_engine_mem_mb": $MEM_USAGE
    }
  }${COMMA}
EOF

    echo "Cooldown for 15s before next iteration..."
    sleep 15
done

echo "]" >> "$RESULTS_FILE"

echo -e "\n${CYAN}======================================================${NC}"
echo -e "${GREEN}Testing Complete. JSON Results saved to $RESULTS_FILE${NC}"
echo "Sample of JSON output:"
head -n 30 "$RESULTS_FILE"
