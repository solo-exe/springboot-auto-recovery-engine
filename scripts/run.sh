#!/bin/bash

# Auto-Recovery Engine Launch System Script
# This script starts the Infrastructure and Observability tiers via Docker
# and sequentially launches the Java microservices in the background,
# logging their output to the logs/ directory. Finally, it opens the
# Spring Boot Admin UI and monitors the logs.

# ---------------------------------------------------------
# Formatting and UI Helpers
# ---------------------------------------------------------
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

log_header() {
  echo -e "\n${CYAN}═══════════════════════════════════════════════════════${NC}"
  echo -e "${CYAN}  $1${NC}"
  echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}"
}
log_step() { echo -e "  ${YELLOW}→${NC} $1"; }
log_ok() { echo -e "  ${GREEN}✅${NC} $1"; }
log_fail() { echo -e "  ${RED}❌${NC} $1"; }
log_warn() { echo -e "  ${YELLOW}⚠️${NC} $1"; }

# ---------------------------------------------------------
# Configuration
# ---------------------------------------------------------
export PATH=$PATH:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

LOG_DIR="$PROJECT_ROOT/logs"
mkdir -p "$LOG_DIR"

# Correct sequential startup order
JAVA_SERVICES=("admin" "account" "payment" "notif" "gateway" "recovery")
INFRA_SERVICES="postgres rabbitmq"
OBS_SERVICES="prometheus loki promtail grafana alertmanager"
DOCKER_MICROSERVICES="payment-service account-service notification-worker api-gateway spring-boot-admin recovery-engine"

if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
else
    DOCKER_COMPOSE="docker compose"
fi
MVN="mvn"

# ---------------------------------------------------------
# Execution
# ---------------------------------------------------------

log_header "TIER 0: Cleanup (Killing stale processes)"
log_step "Stopping any Docker microservice containers..."
$DOCKER_COMPOSE stop $DOCKER_MICROSERVICES 2>/dev/null || true
$DOCKER_COMPOSE rm -f $DOCKER_MICROSERVICES 2>/dev/null || true

log_step "Killing zombie Java and Node processes on required ports (including Grafana's 3000)..."
for port in 3000 8080 8081 8082 8085 8086 8087; do
    lsof -i :"$port" -sTCP:LISTEN | awk 'NR>1 && $1 ~ /java|mvn|node/ {print $2}' | xargs kill -9 2>/dev/null || true
done
log_ok "Ports are clear."

check_container_running() {
    local container=$1
    STATUS=$(docker inspect -f '{{.State.Status}}' "$container" 2>/dev/null || echo "missing")
    if [ "$STATUS" == "running" ]; then
        return 0
    else
        return 1
    fi
}

start_docker_services() {
    local tier_name=$1
    shift
    local services=("$@")
    local services_to_start=""

    for svc in "${services[@]}"; do
        if check_container_running "are-$svc"; then
            log_ok "are-$svc is already running."
        else
            services_to_start="$services_to_start $svc"
        fi
    done

    if [ -n "$services_to_start" ]; then
        log_step "Starting Docker containers in $tier_name: $services_to_start"
        $DOCKER_COMPOSE up -d $services_to_start
    fi
}

log_header "TIER 1: Infrastructure (Postgres + RabbitMQ)"
start_docker_services "Infrastructure" postgres rabbitmq

wait_for_healthy() {
    local container=$1
    log_step "Waiting for $container to become healthy (timeout: 60s)..."
    for i in {1..30}; do
        STATUS=$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null || echo "missing")
        # If the container has no healthcheck, it might just say "running" or have no Health object, but the docker inspect above gets .State.Health.Status
        # Let's handle if it returns empty or missing (for containers without healthcheck)
        if [ "$STATUS" == "healthy" ]; then
            log_ok "$container is healthy!"
            return 0
        fi
        sleep 2
    done
    log_fail "$container failed to become healthy."
    exit 1
}

wait_for_healthy "are-postgres"
wait_for_healthy "are-rabbitmq"

log_header "TIER 2: Observability (Prometheus, Loki, Promtail, Grafana, Alertmanager)"
start_docker_services "Observability" prometheus loki promtail grafana alertmanager

wait_for_healthy "are-loki"
log_step "Giving other observability services 5s to initialize..."
sleep 5
log_ok "Observability tier is up."

log_header "TIER 3 & 4: Application + Recovery Layer"

wait_for_port() {
  local port=$1
  local name=$2
  local pid=$3
  log_step "Waiting for $name to start on port $port..."
  for i in {1..60}; do
    if ! kill -0 "$pid" 2>/dev/null; then
      log_fail "$name CRASHED during startup! Check $LOG_DIR/$name.log for details."
      exit 1
    fi
    if lsof -i :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
      log_ok "$name is now running on port $port!"
      return 0
    fi
    sleep 2
  done
  log_fail "$name failed to start on port $port within 120 seconds."
  exit 1
}

for svc in "${JAVA_SERVICES[@]}"; do
  case $svc in
    "admin")   port=8086; name="spring-boot-admin"; module="services/spring-boot-admin" ;;
    "account") port=8082; name="account-service"; module="services/account-service" ;;
    "payment") port=8081; name="payment-service"; module="services/payment-service" ;;
    "notif")   port=8085; name="notification-worker"; module="services/notification-worker" ;;
    "gateway") port=8080; name="api-gateway"; module="services/api-gateway" ;;
    "recovery") port=8087; name="recovery-engine"; module="services/recovery-engine" ;;
  esac

  log_header "Starting $name (port $port)"
  log_step "Launching in background, logging to $LOG_DIR/$name.log..."
  
  $MVN spring-boot:run -pl "$module" -Dspring-boot.run.jvmArguments="${JVM_MAX_HEAP:- -Xmx512m}" > "$LOG_DIR/$name.log" 2>&1 &
  pid=$!
  echo "$pid" > "$LOG_DIR/$name.pid"

  wait_for_port $port $name $pid
done

log_header "🎉 System Launch Complete"
echo -e "${GREEN}All services have been started in the background!${NC}"
echo ""
echo "Service URLs:"
echo "  API Gateway:       http://localhost:8080"
echo "  Swagger UI:        http://localhost:8080/swagger-ui.html"
echo "  Payment Service:   http://localhost:8081"
echo "  Account Service:   http://localhost:8082"
echo "  Notification:      http://localhost:8085"
echo "  Spring Boot Admin: http://localhost:8086"
echo "  Recovery Engine:   http://localhost:8087"
echo ""
echo "Infrastructure:"
echo "  PostgreSQL:        localhost:15432"
echo "  RabbitMQ:          http://localhost:15672 (guest/guest)"
echo "  Prometheus:        http://localhost:9090"
echo "  Grafana:           http://localhost:3000 (admin/admin)"
echo "  Alertmanager:      http://localhost:9093"
echo "  Loki:              http://localhost:3100"
echo ""

log_step "Opening Spring Boot Admin UI in your default browser..."
if command -v open &> /dev/null; then
    open "http://localhost:8086"
fi

log_header "📊 Service Monitor"
log_step "You can view individual logs in $LOG_DIR/"
echo -e "Press ${YELLOW}Ctrl+C${NC} to stop tailing logs. Services will remain running in the background."
echo -e "To stop services later, run: ${CYAN}./scripts/launch_engine.sh stop${NC}"
echo ""
log_step "Tailing all service logs..."
tail -f "$LOG_DIR"/*.log
