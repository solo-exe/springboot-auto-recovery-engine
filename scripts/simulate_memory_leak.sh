#!/bin/bash

# ============================================================
# Performance Test: Memory Leak Scenario (Academic Edition)
# Measures MTTR, Latencies, and Overhead. Outputs to JSON.
# ============================================================

set -e

SERVICES=("account-service" "payment-service")
PORTS=(8082 8081)
SHORTS=("account" "payment")
RESULTS_FILE="logs/memory_leak_results.json"

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

get_heap_usage_percent() {
    local port=$1
    python3 -c "
import urllib.request
try:
    req = urllib.request.urlopen('http://localhost:$port/actuator/prometheus')
    content = req.read().decode('utf-8')
    used_val = 0.0
    max_val = 0.0
    for line in content.split('\n'):
        if 'area=\"heap\"' in line:
            if line.startswith('jvm_memory_used_bytes'):
                parts = line.strip().split()
                if len(parts) >= 2:
                    used_val += float(parts[-1])
            elif line.startswith('jvm_memory_max_bytes'):
                parts = line.strip().split()
                if len(parts) >= 2:
                    val = float(parts[-1])
                    if val > 0:
                        max_val += val
    used_mb = used_val / (1024 * 1024)
    max_mb = max_val / (1024 * 1024) if max_val > 0 else 256.0
    percent = (used_mb / max_mb) * 100 if max_mb > 0 else 0.0
    print(f'{used_mb:.1f}MB / {max_mb:.1f}MB ({percent:.1f}%)')
except Exception as e:
    print('N/A - ' + str(e))
"
}

restart_with_custom_heap() {
    local short_name=$1
    local port=$2
    local heap_size=$3
    
    if lsof -i :"$port" -sTCP:LISTEN &>/dev/null; then
        echo -e "${YELLOW}Stopping existing process on port $port...${NC}"
        lsof -i :"$port" -sTCP:LISTEN | awk 'NR>1 && $1 ~ /java|mvn/ {print $2}' | xargs kill -9 2>/dev/null || true
        while lsof -i :"$port" -sTCP:LISTEN &>/dev/null; do
            sleep 0.5
        done
    fi

    echo -e "${YELLOW}Starting $short_name with JVM heap limit $heap_size...${NC}"
    export JVM_MAX_HEAP="$heap_size"
    ./scripts/debug_launch.sh $short_name > /dev/null 2>&1 &
    wait_for_health $port
    echo -e "${GREEN}$short_name is up and running with custom heap.${NC}"
    sleep 5 # stabilization buffer
}

restart_with_default_heap() {
    local short_name=$1
    local port=$2

    if lsof -i :"$port" -sTCP:LISTEN &>/dev/null; then
        echo -e "${YELLOW}Stopping custom-heap process on port $port...${NC}"
        lsof -i :"$port" -sTCP:LISTEN | awk 'NR>1 && $1 ~ /java|mvn/ {print $2}' | xargs kill -9 2>/dev/null || true
        while lsof -i :"$port" -sTCP:LISTEN &>/dev/null; do
            sleep 0.5
        done
    fi

    echo -e "${YELLOW}Starting $short_name back with default JVM heap...${NC}"
    unset JVM_MAX_HEAP
    ./scripts/debug_launch.sh $short_name > /dev/null 2>&1 &
    wait_for_health $port
    echo -e "${GREEN}$short_name has been recovered and is healthy with default heap.${NC}"
    sleep 5 # stabilization buffer
}

# Record the exact trigger time of the script execution
TRIGGER_TIME=$(python3 -c 'from datetime import datetime; print(datetime.now().astimezone().isoformat())')

echo -e "${CYAN}Starting Memory Leak Performance Test...${NC}"
echo -e "${CYAN}Trigger Time: $TRIGGER_TIME${NC}"
echo -e "${CYAN}Output will be appended incrementally to $RESULTS_FILE${NC}"

for idx in 0 1; do
    SERVICE_NAME=${SERVICES[$idx]}
    PORT=${PORTS[$idx]}
    SHORT_NAME=${SHORTS[$idx]}

    echo -e "\n${CYAN}--- Simulating Memory Leak on $SERVICE_NAME (Port: $PORT) ---${NC}"

    # 1. Restart the service with a small heap limit to speed up the memory leak alert
    restart_with_custom_heap $SHORT_NAME $PORT "-Xmx256m"

    # Get initial log line count so we only search new logs
    LOG_START_LINE=$(get_recovery_log_lines)

    # 2. Inject Fault (Memory Leak)
    echo "Injecting Memory Leak on $SERVICE_NAME (allocating 1MB/500ms)..."
    T_FAULT=$(current_time_ms)

    # Set the fault (enable=true, maxMb=-1) with retries
    inject_success=false
    for attempt in {1..10}; do
        if curl -s -X POST -H "Content-Type: application/json" -d '{"enable": true, "maxMb": -1}' "http://localhost:$PORT/fault/memory-leak" > /dev/null; then
            inject_success=true
            break
        fi
        echo "Injection failed, retrying in 2 seconds (attempt $attempt/10)..."
        sleep 2
    done

    if [ "$inject_success" = false ]; then
        echo -e "${RED}Failed to inject memory leak fault on $SERVICE_NAME after multiple attempts.${NC}"
        exit 1
    fi

    # 3. Wait for Detection by Recovery Engine (HighMemoryUsage alert)
    echo "Waiting for Recovery Engine to detect HighMemoryUsage..."
    T_DETECT=0
    TIMEOUT=240
    ELAPSED=0
    while [ $ELAPSED -lt $TIMEOUT ]; do
        # Log heap usage every 5 seconds (10 iterations of 0.5s)
        if [ $((ELAPSED % 10)) -eq 0 ]; then
            usage=$(get_heap_usage_percent $PORT)
            echo -e "${YELLOW}[MONITOR] Current Heap Usage: $usage${NC}"
        fi

        match=$(tail -n +$LOG_START_LINE logs/recovery-engine.log | grep "Matched rule \[Memory Leak\] for alert \[HighMemoryUsage\]" | tail -n 1)
        if [ ! -z "$match" ]; then
            T_DETECT=$(current_time_ms)
            echo -e "${GREEN}[LIVE EVENT] $match${NC}"
            break
        fi
        sleep 0.5
        ELAPSED=$((ELAPSED + 1))
    done

    if [ $T_DETECT -eq 0 ]; then
        echo -e "${RED}Recovery Engine did not detect the memory leak within timeout.${NC}"
        # Cleanup: disable memory leak and restart with default heap
        curl -s -X POST -H "Content-Type: application/json" -d '{"enable": false}' "http://localhost:$PORT/fault/memory-leak" > /dev/null
        restart_with_default_heap $SHORT_NAME $PORT
        exit 1
    fi

    DETECT_LATENCY=$((T_DETECT - T_FAULT))
    echo -e "${GREEN}Detected! Latency: ${DETECT_LATENCY}ms${NC}"

    # 4. Wait for Execution (RESTART action command logged)
    T_EXEC_FINISH=0
    while true; do
        match=$(tail -n +$LOG_START_LINE logs/recovery-engine.log | grep "Sending RESTART command to Spring Boot Admin for instance $SERVICE_NAME" | tail -n 1)
        if [ ! -z "$match" ]; then
            T_EXEC_FINISH=$(current_time_ms)
            echo -e "${GREEN}[LIVE EVENT] $match${NC}"
            break
        fi
        sleep 0.1
    done
    EXEC_LATENCY=$((T_EXEC_FINISH - T_DETECT))
    echo -e "${GREEN}Remediation Executed! Execution Latency: ${EXEC_LATENCY}ms${NC}"

    # Also log execution success message if found
    success_match=$(tail -n +$LOG_START_LINE logs/recovery-engine.log | grep "Successfully executed RESTART on $SERVICE_NAME" | tail -n 1)
    if [ ! -z "$success_match" ]; then
        echo -e "${GREEN}[LIVE EVENT] $success_match${NC}"
    fi

    # 5. Measure Overhead
    OVERHEAD=$(get_recovery_overhead)
    CPU_USAGE=$(echo "$OVERHEAD" | cut -d',' -f1)
    MEM_USAGE=$(echo "$OVERHEAD" | cut -d',' -f2)
    echo -e "${CYAN}Recovery Engine Overhead - CPU: ${CPU_USAGE}%, Mem: ${MEM_USAGE}MB${NC}"

    # 6. Execute OS-level recovery restart and restore default heap
    restart_with_default_heap $SHORT_NAME $PORT
    T_RECOVER=$(current_time_ms)

    MTTR=$((T_RECOVER - T_FAULT))
    echo -e "${GREEN}Recovered! MTTR: ${MTTR}ms${NC}"

    # Save variables for the service
    if [ "$SERVICE_NAME" == "account-service" ]; then
        ACCOUNT_T_FAULT=$T_FAULT
        ACCOUNT_T_DETECT=$T_DETECT
        ACCOUNT_T_RECOVER=$T_RECOVER
        ACCOUNT_DETECT_LATENCY=$DETECT_LATENCY
        ACCOUNT_EXEC_LATENCY=$EXEC_LATENCY
        ACCOUNT_MTTR=$MTTR
        ACCOUNT_CPU=$CPU_USAGE
        ACCOUNT_MEM=$MEM_USAGE
    else
        PAYMENT_T_FAULT=$T_FAULT
        PAYMENT_T_DETECT=$T_DETECT
        PAYMENT_T_RECOVER=$T_RECOVER
        PAYMENT_DETECT_LATENCY=$DETECT_LATENCY
        PAYMENT_EXEC_LATENCY=$EXEC_LATENCY
        PAYMENT_MTTR=$MTTR
        PAYMENT_CPU=$CPU_USAGE
        PAYMENT_MEM=$MEM_USAGE
    fi

    # Cool down before the next service to allow metrics to stabilize
    echo "Cooldown for 10s before next service..."
    sleep 10
done

# 7. Write metrics output using Python to ensure valid JSON appending
echo -e "\n${CYAN}Saving metrics to $RESULTS_FILE...${NC}"
python3 - <<EOF
import json
import os

results_file = "$RESULTS_FILE"
data = []
if os.path.exists(results_file):
    try:
        with open(results_file, "r") as f:
            content = f.read().strip()
            if content:
                data = json.loads(content)
    except Exception as e:
        data = []

if not isinstance(data, list):
    data = []

run_entry = {
    "trigger_time": "$TRIGGER_TIME",
    "account-service": {
        "timestamps": {
            "t_fault_ms": int("$ACCOUNT_T_FAULT"),
            "t_detect_ms": int("$ACCOUNT_T_DETECT"),
            "t_recover_ms": int("$ACCOUNT_T_RECOVER")
        },
        "metrics": {
            "detection_latency_ms": int("$ACCOUNT_DETECT_LATENCY"),
            "execution_latency_ms": int("$ACCOUNT_EXEC_LATENCY"),
            "mttr_ms": int("$ACCOUNT_MTTR")
        },
        "overhead": {
            "recovery_engine_cpu_percent": float("$ACCOUNT_CPU"),
            "recovery_engine_mem_mb": float("$ACCOUNT_MEM")
        }
    },
    "payment-service": {
        "timestamps": {
            "t_fault_ms": int("$PAYMENT_T_FAULT"),
            "t_detect_ms": int("$PAYMENT_T_DETECT"),
            "t_recover_ms": int("$PAYMENT_T_RECOVER")
        },
        "metrics": {
            "detection_latency_ms": int("$PAYMENT_DETECT_LATENCY"),
            "execution_latency_ms": int("$PAYMENT_EXEC_LATENCY"),
            "mttr_ms": int("$PAYMENT_MTTR")
        },
        "overhead": {
            "recovery_engine_cpu_percent": float("$PAYMENT_CPU"),
            "recovery_engine_mem_mb": float("$PAYMENT_MEM")
        }
    }
}

data.append(run_entry)

with open(results_file, "w") as f:
    json.dump(data, f, indent=2)
EOF

echo -e "${GREEN}Metrics recorded successfully!${NC}"
echo -e "\n${CYAN}======================================================${NC}"
echo -e "${GREEN}Testing Complete. JSON Results saved to $RESULTS_FILE${NC}"
