#!/bin/bash

# Auto-Recovery Engine Launch System Script
# This script starts the Infrastructure and Observability tiers via Docker
# and launches the Java microservices in separate terminal windows.

# Configuration
export PATH=$PATH:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin

JAVA_SERVICES=("gateway" "payment" "account" "notif" "recovery" "admin")
INFRA_SERVICES="postgres rabbitmq"
OBS_SERVICES="prometheus loki promtail grafana alertmanager"

# Command detection
if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
else
    DOCKER_COMPOSE="docker compose"
fi

# 1. Start Docker containers
echo "🚀 Starting Infrastructure Tier ($INFRA_SERVICES)..."
$DOCKER_COMPOSE up -d $INFRA_SERVICES

echo "🚀 Starting Observability Tier ($OBS_SERVICES)..."
$DOCKER_COMPOSE up -d $OBS_SERVICES

# 2. Wait for infrastructure and observability
echo "⏳ Waiting for key services to be healthy..."

wait_for_healthy() {
    local container=$1
    echo "   Checking health for $container..."
    # Loop until healthy
    while true; do
        STATUS=$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null)
        if [ "$STATUS" == "healthy" ]; then
            break
        fi
        printf "."
        sleep 2
    done
    echo ""
    echo "   ✅ $container is healthy!"
}

# Wait for core dependencies
wait_for_healthy "are-postgres"
wait_for_healthy "are-rabbitmq"
wait_for_healthy "are-loki"

# Small buffer for other observability tools to initialize
echo "💤 Giving other services 5 seconds to settle..."
sleep 5

# 3. Launch Java services in separate terminals
PROJECT_ROOT=$(pwd)

for service in "${JAVA_SERVICES[@]}"; do
    echo "🏗️ Launching start-$service in a new terminal window..."
    osascript <<EOF
tell application "Terminal"
    do script "cd '$PROJECT_ROOT' && make start-$service"
    activate
end tell
EOF
done

echo "✅ All tiers launched successfully!"
echo "Check your terminal windows for service logs."
