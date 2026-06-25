#!/bin/bash

# Auto-Recovery Engine Launch System Script (Sequential + Terminal Windows)
# This script starts the Infrastructure and Observability tiers via Docker
# and sequentially launches the Java microservices in separate terminal windows,
# waiting for each to become healthy before proceeding to the next.

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
PROJECT_ROOT=$(pwd)

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

# ---------------------------------------------------------
# Execution
# ---------------------------------------------------------

log_header "TIER 0: Cleanup (Killing stale processes)"
log_step "Stopping any Docker microservice containers..."
$DOCKER_COMPOSE stop $DOCKER_MICROSERVICES 2>/dev/null || true
$DOCKER_COMPOSE rm -f $DOCKER_MICROSERVICES 2>/dev/null || true

log_step "Killing zombie Java processes on ports 8080-8087..."
for port in 8080 8081 8082 8085 8086 8087; do
    lsof -i :"$port" -sTCP:LISTEN | awk 'NR>1 && $1 ~ /java|mvn/ {print $2}' | xargs kill -9 2>/dev/null || true
done
log_ok "Ports are clear."

log_header "TIER 1: Infrastructure (Postgres + RabbitMQ)"
log_step "Starting Docker containers..."
$DOCKER_COMPOSE up -d $INFRA_SERVICES

wait_for_healthy() {
    local container=$1
    log_step "Waiting for $container to become healthy (timeout: 60s)..."
    for i in {1..30}; do
        STATUS=$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null)
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
log_step "Starting Docker containers..."
$DOCKER_COMPOSE up -d $OBS_SERVICES
wait_for_healthy "are-loki"
log_step "Giving other observability services 5s to initialize..."
sleep 5
log_ok "Observability tier is up."

log_header "TIER 3 & 4: Application + Recovery Layer"

wait_for_port() {
  local port=$1
  local name=$2
  log_step "Waiting for $name to start on port $port..."
  for i in {1..60}; do
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
    "admin")   port=8086; name="spring-boot-admin" ;;
    "account") port=8082; name="account-service" ;;
    "payment") port=8081; name="payment-service" ;;
    "notif")   port=8085; name="notification-worker" ;;
    "gateway") port=8080; name="api-gateway" ;;
    "recovery") port=8087; name="recovery-engine" ;;
  esac

  log_header "Starting $name (port $port)"
  log_step "Launching make start-$svc in a new terminal window..."
  
  osascript <<EOF
tell application "Terminal"
    do script "cd '$PROJECT_ROOT' && make start-$svc"
    activate
end tell
EOF

  wait_for_port $port $name
done

log_header "🎉 System Launch Complete"
echo -e "${GREEN}All services have been started sequentially and verified!${NC}"
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
echo "  PostgreSQL:        localhost:5432"
echo "  RabbitMQ:          http://localhost:15672 (guest/guest)"
echo "  Prometheus:        http://localhost:9090"
echo "  Grafana:           http://localhost:3000 (admin/admin)"
echo "  Alertmanager:      http://localhost:9093"
echo "  Loki:              http://localhost:3100"
echo ""
