#!/bin/bash

# ============================================================
# Auto-Recovery Engine — Debug Launch Script
# ============================================================
# This script starts each tier/service sequentially with full
# output in the current terminal so you can spot failures.
#
# Architecture Tiers (from are_arch.png):
#   1. INFRASTRUCTURE TIER  — Postgres, RabbitMQ
#   2. OBSERVABILITY LAYER  — Prometheus, Loki, Promtail, Grafana, Alertmanager
#   3. APPLICATION LAYER    — Spring Boot Admin, Account, Payment, Notification, API Gateway
#   4. RECOVERY LAYER       — Recovery Engine
#
# Usage:
#   ./scripts/debug_launch.sh              # Start everything
#   ./scripts/debug_launch.sh infra        # Start only infrastructure
#   ./scripts/debug_launch.sh obs          # Start only observability
#   ./scripts/debug_launch.sh build        # Maven build only
#   ./scripts/debug_launch.sh <service>    # Start a single Java service
#       service = admin | account | payment | notif | gateway | recovery
#   ./scripts/debug_launch.sh status       # Check status of all components
#   ./scripts/debug_launch.sh stop         # Stop everything
# ============================================================

set -euo pipefail

# Force Java to use IPv4 and bind to all interfaces so Docker's host.docker.internal bridge can reach it
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Dserver.address=0.0.0.0"

# --- Colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# --- Project root (always resolve to script's parent dir) ---
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# --- Log directory ---
LOG_DIR="$PROJECT_ROOT/logs"
mkdir -p "$LOG_DIR"

# --- Docker Compose detection ---
if command -v docker-compose &> /dev/null; then
    DC="docker-compose"
else
    DC="docker compose"
fi

MVN="mvn"

# ============================================================
# Helper functions
# ============================================================

log_header() {
    echo ""
    echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BOLD}${BLUE}  $1${NC}"
    echo -e "${BOLD}${BLUE}═══════════════════════════════════════════════════════${NC}"
}

log_step() {
    echo -e "${CYAN}  → $1${NC}"
}

log_ok() {
    echo -e "${GREEN}  ✅ $1${NC}"
}

log_warn() {
    echo -e "${YELLOW}  ⚠️  $1${NC}"
}

log_fail() {
    echo -e "${RED}  ❌ $1${NC}"
}

# Wait for a Docker container to report healthy
wait_for_healthy() {
    local container="$1"
    local timeout="${2:-120}" # default 120s
    local elapsed=0
    log_step "Waiting for $container to become healthy (timeout: ${timeout}s)..."
    while [ $elapsed -lt $timeout ]; do
        STATUS=$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null || echo "not_found")
        case "$STATUS" in
            healthy)
                log_ok "$container is healthy!"
                return 0
                ;;
            not_found)
                log_fail "$container container not found!"
                return 1
                ;;
        esac
        printf "."
        sleep 2
        elapsed=$((elapsed + 2))
    done
    echo ""
    log_fail "$container did not become healthy within ${timeout}s"
    docker logs --tail 30 "$container" 2>&1 || true
    return 1
}

# Check if a port is already in use
check_port() {
    local port="$1"
    local service_name="$2"
    if lsof -i :"$port" -sTCP:LISTEN &>/dev/null; then
        log_warn "Port $port ($service_name) is already in use!"
        lsof -i :"$port" -sTCP:LISTEN 2>/dev/null | head -5
        return 1
    else
        log_ok "Port $port ($service_name) is available"
        return 0
    fi
}

# Start a Java service in the background with logging
start_java_service() {
    local service_name="$1"
    local maven_module="$2"
    local port="$3"
    local log_file="$LOG_DIR/${service_name}.log"

    log_header "Starting $service_name (port $port)"

    # Check port
    if ! check_port "$port" "$service_name"; then
        log_warn "Killing existing Java process on port $port..."
        lsof -i :"$port" -sTCP:LISTEN | awk 'NR>1 && $1 ~ /java|mvn/ {print $2}' | xargs kill -9 2>/dev/null || true
        sleep 2
    fi

    log_step "Running: $MVN spring-boot:run -pl $maven_module"
    log_step "Log file: $log_file"

    # Launch in background, tee to log file
    $MVN spring-boot:run -pl "$maven_module" > "$log_file" 2>&1 &
    local pid=$!
    echo "$pid" > "$LOG_DIR/${service_name}.pid"
    log_step "PID: $pid"

    # Wait for startup (check if process is still running and port opens)
    log_step "Waiting for $service_name to start on port $port..."
    local wait_time=0
    local max_wait=90
    while [ $wait_time -lt $max_wait ]; do
        # Check if process died
        if ! kill -0 "$pid" 2>/dev/null; then
            echo ""
            log_fail "$service_name CRASHED during startup!"
            echo -e "${RED}  Last 50 lines of log:${NC}"
            tail -50 "$log_file"
            return 1
        fi
        # Check if port is listening
        if lsof -i :"$port" -sTCP:LISTEN &>/dev/null; then
            echo ""
            log_ok "$service_name is running on port $port (PID: $pid)"
            return 0
        fi
        printf "."
        sleep 2
        wait_time=$((wait_time + 2))
    done
    echo ""
    log_fail "$service_name did not start within ${max_wait}s"
    echo -e "${RED}  Last 50 lines of log:${NC}"
    tail -50 "$log_file"
    return 1
}

# ============================================================
# Tier launchers
# ============================================================

start_infra() {
    log_header "TIER 1: Infrastructure (Postgres + RabbitMQ)"

    log_step "Starting Docker containers..."
    $DC up -d --remove-orphans postgres rabbitmq

    wait_for_healthy "are-postgres" 60
    wait_for_healthy "are-rabbitmq" 60
}

start_obs() {
    log_header "TIER 2: Observability (Prometheus, Loki, Promtail, Grafana, Alertmanager)"

    log_step "Starting Docker containers..."
    $DC up -d --remove-orphans prometheus loki promtail grafana alertmanager

    wait_for_healthy "are-loki" 60

    log_step "Giving other observability services 5s to initialize..."
    sleep 5

    # Quick connectivity checks
    for svc_port in "are-prometheus:9090" "are-grafana:3000" "are-alertmanager:9093"; do
        local svc="${svc_port%%:*}"
        local port="${svc_port##*:}"
        if curl -sf "http://localhost:$port" > /dev/null 2>&1; then
            log_ok "$svc is responding on port $port"
        else
            log_warn "$svc is not responding yet on port $port (may still be starting)"
        fi
    done
}

do_build() {
    log_header "Maven Build (skip tests)"
    log_step "Running: $MVN clean install -DskipTests"
    if $MVN clean install -DskipTests; then
        log_ok "Build succeeded!"
    else
        log_fail "Build FAILED!"
        return 1
    fi
}

# Start order follows the architecture:
#   1. admin (standalone, no deps on other services)
#   2. account-service (needs Postgres, RabbitMQ)
#   3. payment-service (needs Postgres, RabbitMQ, account-service)
#   4. notification-worker (needs RabbitMQ)
#   5. api-gateway (routes to account, payment, notification)
#   6. recovery-engine (needs all of the above + observability)

start_admin()    { start_java_service "spring-boot-admin"   "services/spring-boot-admin"   8086; }
start_account()  { start_java_service "account-service"     "services/account-service"     8082; }
start_payment()  { start_java_service "payment-service"     "services/payment-service"     8081; }
start_notif()    { start_java_service "notification-worker"  "services/notification-worker" 8085; }
start_gateway()  { start_java_service "api-gateway"         "services/api-gateway"         8080; }
start_recovery() { start_java_service "recovery-engine"     "services/recovery-engine"     8087; }

start_all_services() {
    log_header "TIER 3 & 4: Application + Recovery Layer"

    local failed=0

    start_admin    || ((failed++))
    start_account  || ((failed++))
    start_payment  || ((failed++))
    start_notif    || ((failed++))
    start_gateway  || ((failed++))
    start_recovery || ((failed++))

    if [ $failed -gt 0 ]; then
        log_fail "$failed service(s) failed to start. Check logs in $LOG_DIR/"
        return 1
    else
        log_ok "All services started successfully!"
    fi
}

# ============================================================
# Status check
# ============================================================

check_status() {
    log_header "System Status"

    # Docker containers
    echo -e "\n${BOLD}Docker Containers:${NC}"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null | grep -E "are-|NAMES" || log_warn "Cannot connect to Docker"

    # Java services
    echo -e "\n${BOLD}Java Services:${NC}"
    local services=("spring-boot-admin:8086" "account-service:8082" "payment-service:8081" "notification-worker:8085" "api-gateway:8080" "recovery-engine:8087")
    for svc_port in "${services[@]}"; do
        local svc="${svc_port%%:*}"
        local port="${svc_port##*:}"
        local pid_file="$LOG_DIR/${svc}.pid"

        # Check PID
        local pid_status="no PID file"
        if [ -f "$pid_file" ]; then
            local pid=$(cat "$pid_file")
            if kill -0 "$pid" 2>/dev/null; then
                pid_status="PID $pid running"
            else
                pid_status="PID $pid DEAD"
            fi
        fi

        # Check port
        if lsof -i :"$port" -sTCP:LISTEN &>/dev/null; then
            log_ok "$svc (port $port) — UP — $pid_status"
        else
            log_fail "$svc (port $port) — DOWN — $pid_status"
        fi
    done

    # Health endpoints
    echo -e "\n${BOLD}Health Endpoints:${NC}"
    for svc_port in "${services[@]}"; do
        local svc="${svc_port%%:*}"
        local port="${svc_port##*:}"
        local health=$(curl -sf "http://localhost:$port/actuator/health" 2>/dev/null || echo '{"status":"UNREACHABLE"}')
        local status=$(echo "$health" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
        if [ "$status" = "UP" ]; then
            log_ok "$svc — $status"
        else
            log_fail "$svc — ${status:-UNREACHABLE}"
        fi
    done
}

# ============================================================
# Stop everything
# ============================================================

stop_all() {
    log_header "Stopping All Services"

    # Kill Java services
    log_step "Stopping Java services..."
    for pid_file in "$LOG_DIR"/*.pid; do
        [ -f "$pid_file" ] || continue
        local svc=$(basename "$pid_file" .pid)
        local pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            log_step "Stopping $svc (PID $pid)..."
            kill "$pid" 2>/dev/null || true
        fi
        rm -f "$pid_file"
    done

    # Also kill any remaining Maven/Java processes on our ports
    for port in 8080 8081 8082 8085 8086 8087; do
        lsof -i :"$port" -sTCP:LISTEN | awk 'NR>1 && $1 ~ /java|mvn/ {print $2}' | xargs kill -9 2>/dev/null || true
    done

    sleep 2

    # Stop Docker containers
    log_step "Stopping Docker containers..."
    $DC down --remove-orphans

    log_ok "All services stopped."
}

# ============================================================
# View logs for a service
# ============================================================

view_logs() {
    local service="$1"
    local log_file="$LOG_DIR/${service}.log"
    if [ -f "$log_file" ]; then
        tail -100 "$log_file"
    else
        log_fail "No log file found for $service at $log_file"
        echo "Available logs:"
        ls "$LOG_DIR"/*.log 2>/dev/null || echo "  (none)"
    fi
}

# ============================================================
# Main entrypoint
# ============================================================

case "${1:-all}" in
    infra)
        start_infra
        ;;
    obs|observability)
        start_obs
        ;;
    build)
        do_build
        ;;
    admin)
        start_admin
        ;;
    account)
        start_account
        ;;
    payment)
        start_payment
        ;;
    notif|notification)
        start_notif
        ;;
    gateway)
        start_gateway
        ;;
    recovery)
        start_recovery
        ;;
    services)
        start_all_services
        ;;
    status)
        check_status
        ;;
    stop)
        stop_all
        ;;
    logs)
        if [ -z "${2:-}" ]; then
            echo "Usage: $0 logs <service-name>"
            echo "Services: spring-boot-admin, account-service, payment-service, notification-worker, api-gateway, recovery-engine"
            exit 1
        fi
        view_logs "$2"
        ;;
    all)
        log_header "🚀 AUTO-RECOVERY ENGINE — Full System Launch"
        echo -e "${CYAN}  Startup order:${NC}"
        echo "    1. Infrastructure Tier  (Postgres, RabbitMQ)"
        echo "    2. Observability Layer  (Prometheus, Loki, Grafana, Alertmanager)"
        echo "    3. Application Layer    (Admin, Account, Payment, Notification, Gateway)"
        echo "    4. Recovery Layer       (Recovery Engine)"
        echo ""

        start_infra
        start_obs
        start_all_services

        log_header "🎉 Launch Complete"
        echo ""
        echo -e "${BOLD}Service URLs:${NC}"
        echo "  API Gateway:       http://localhost:8080"
        echo "  Swagger UI:        http://localhost:8080/swagger-ui.html"
        echo "  Payment Service:   http://localhost:8081"
        echo "  Account Service:   http://localhost:8082"
        echo "  Notification:      http://localhost:8085"
        echo "  Spring Boot Admin: http://localhost:8086"
        echo "  Recovery Engine:   http://localhost:8087"
        echo ""
        echo -e "${BOLD}Infrastructure:${NC}"
        echo "  PostgreSQL:        localhost:5432"
        echo "  RabbitMQ:          http://localhost:15672 (guest/guest)"
        echo "  Prometheus:        http://localhost:9090"
        echo "  Grafana:           http://localhost:3000 (admin/admin)"
        echo "  Alertmanager:      http://localhost:9093"
        echo "  Loki:              http://localhost:3100"
        echo ""
        echo -e "${BOLD}Logs:${NC} $LOG_DIR/"
        echo -e "${BOLD}Status:${NC} ./scripts/debug_launch.sh status"
        echo -e "${BOLD}Stop:${NC}   ./scripts/debug_launch.sh stop"
        ;;
    *)
        echo "Usage: $0 {all|infra|obs|build|services|admin|account|payment|notif|gateway|recovery|status|stop|logs <service>}"
        exit 1
        ;;
esac
