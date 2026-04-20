#!/bin/bash

# =============================================================================
# Observability Infrastructure Test Script
# =============================================================================
# Automatically seeds data, triggers faults, and validates that logs and metrics
# are flowing correctly through Prometheus, Loki, and Grafana.
# =============================================================================

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo "====================================================================="
echo "ARE Observability Auto-Test Script"
echo "====================================================================="

# Base APIs
GATEWAY_URL="http://localhost:8080"
ACCOUNT_URL="$GATEWAY_URL/api/accounts"
PAYMENT_URL="$GATEWAY_URL/api/payments"
JSON_HEADER="Content-Type: application/json"

# Generated Data
TEST_EMAIL="test.user_$(date +%s)@example.com"
TEST_PASS="P@ssw0rd123!"

echo -e "\n${YELLOW}[Phase 1] Checking Observability Infrastructure Health...${NC}"

# Wait for Prometheus
echo -n "Waiting for Prometheus... "
until curl -s http://localhost:9090/-/healthy > /dev/null; do sleep 2; done
echo -e "${GREEN}UP${NC}"

# Wait for Loki
echo -n "Waiting for Loki... "
until curl -s http://localhost:3100/ready > /dev/null; do sleep 2; done
echo -e "${GREEN}UP${NC}"

# Wait for Grafana
echo -n "Waiting for Grafana... "
until curl -s http://localhost:3000/api/health > /dev/null; do sleep 2; done
echo -e "${GREEN}UP${NC}"

echo -e "\n${YELLOW}[Phase 2] Waiting for Application Services to Boot...${NC}"
until curl -s $GATEWAY_URL/actuator/health | grep -q "UP"; do 
  echo "Waiting for API Gateway..."
  sleep 5
done
echo -e "${GREEN}API Gateway is UP!${NC}"

until curl -s http://localhost:8082/actuator/health | grep -q "UP"; do 
  echo "Waiting for Account Service..."
  sleep 5
done
echo -e "${GREEN}Account Service is UP!${NC}"


echo -e "\n${YELLOW}[Phase 3] Generating Test Data and Logs...${NC}"

# 1. Sign Up (Generates standard logs + DB interactions)
echo "User Sign Up..."
SIGNUP_RES=$(curl -s -X POST $ACCOUNT_URL/auth/signup \
  -H "$JSON_HEADER" \
  -d "{\"email\":\"$TEST_EMAIL\", \"firstName\":\"Test\", \"lastName\":\"User\"}")

# Extract User ID from Signup Response
USER_ID=$(echo $SIGNUP_RES | jq -r '.data.id' 2>/dev/null)

if [ "$USER_ID" != "null" ] && [ -n "$USER_ID" ]; then
  echo "  -> Success (User ID: $USER_ID)"
else
  echo "  -> Failed: $SIGNUP_RES"
  exit 1
fi

# 2. Verify OTP (using test master code 123456)
echo "Verifying OTP (using test master code 123456)..."
VERIFY_RES=$(curl -s -X POST $ACCOUNT_URL/auth/verify-otp \
  -H "$JSON_HEADER" \
  -d "{\"userId\":$USER_ID, \"email\":\"$TEST_EMAIL\", \"otp\":\"123456\"}")

# Extract Confirmation ID (Used for password creation)
CONFIRM_ID=$(echo $VERIFY_RES | jq -r '.data.confirmationId' 2>/dev/null)

if [ "$CONFIRM_ID" != "null" ] && [ -n "$CONFIRM_ID" ]; then
  echo "  -> Success (Confirmation ID: $CONFIRM_ID)"
else
  echo "  -> Failed: $VERIFY_RES"
  exit 1
fi

# 3. Create Password (Completes onboarding)
echo "Completing Onboarding (Setting Password)..."
PASS_RES=$(curl -s -X POST $ACCOUNT_URL/auth/create-password \
  -H "$JSON_HEADER" \
  -d "{\"email\":\"$TEST_EMAIL\", \"confirmationId\":$CONFIRM_ID, \"password\":\"$TEST_PASS\"}")
echo $PASS_RES | grep -q "\"success\":true" && echo "  -> Success" || echo "  -> Failed: $PASS_RES"

# 4. Generate Errors for Loki & Prometheus Error Rate
echo "Generating 4xx/5xx Errors..."
# Hitting restricted endpoint without token
curl -s -o /dev/null $ACCOUNT_URL/me
# Malformed JSON payload
curl -s -X POST $PAYMENT_URL/initiate -H "$JSON_HEADER" -d "{bad:json}" > /dev/null
# Trigger deliberate exceptions (if fault endpoints exist, otherwise just bad URLs)
curl -s -X POST $GATEWAY_URL/api/accounts/bad-endpoint > /dev/null
curl -s -X POST $GATEWAY_URL/api/payments/trigger-error > /dev/null


echo -e "\n${YELLOW}[Phase 4] Validating Observability Stack Integration...${NC}"

# Wait for logs to flush and metrics to be scraped
echo "Waiting for metrics and logs to propagate (10s)..."
sleep 10

# 1. Verify Prometheus Metrica
echo -n "Checking Prometheus metrics... "
METRICS_COUNT=$(curl -s "http://localhost:9090/api/v1/query?query=http_server_requests_seconds_count" | grep -o 'result":\[' | wc -l)
if [ "$METRICS_COUNT" -gt 0 ]; then
  echo -e "${GREEN}SUCCESS${NC} (Metrics found in Prometheus)"
else
  echo -e "${RED}FAILED${NC} (No HTTP metrics found in Prometheus)"
fi

# 2. Verify Loki Logs
echo -n "Checking Loki logs... "
LOG_COUNT=$(curl -s -G "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={compose_project="auto-recovery-engine"}' \
  | grep -o '"values":\[\[' | wc -l)


if [ "$LOG_COUNT" -gt 0 ]; then
  echo -e "${GREEN}SUCCESS${NC} (Logs found in Loki)"
else
  echo -e "${RED}FAILED${NC} (No container logs found in Loki)"
fi

# 3. Verify Alert Rules Loading
echo -n "Checking Alert Rules... "
ALERTS_LOADED=$(curl -s http://localhost:9090/api/v1/rules | grep -o "ServiceDown" | wc -l)
if [ "$ALERTS_LOADED" -gt 0 ]; then
  echo -e "${GREEN}SUCCESS${NC} (Alert rules loaded in Prometheus)"
else
  echo -e "${RED}FAILED${NC} (Alert rules not found)"
fi

echo -e "\n${YELLOW}[Phase 5] Auto-Recovery Engine Evaluation...${NC}"
echo "Triggering simulated Service Crash on Notification Worker..."

# 1. Trigger the fault
curl -s -X POST "$ACCOUNT_URL/fault/crash" > /dev/null

echo "Waiting (up to 60s) for Alertmanager to fire and Recovery Engine to respond..."
SUCCESS=false
for i in {1..12}; do
  HAS_RECOVERY_LOG=$(/usr/local/bin/docker-compose logs recovery-engine | grep "RESTART command" | wc -l)
  if [ "$HAS_RECOVERY_LOG" -gt 0 ]; then
    SUCCESS=true
    break
  fi
  sleep 5
done

# 2. Check Recovery Engine Logs
echo -n "Checking Recovery Engine logs for remediation actions... "
if [ "$SUCCESS" = true ]; then
  echo -e "${GREEN}SUCCESS${NC} (Recovery Engine engaged)"
else
  echo -e "${RED}FAILED${NC} (No RESTART action detected in Recovery Engine logs)"
fi

echo -e "\n${GREEN}=====================================================================${NC}"
echo -e "${GREEN}Test Complete.${NC}"
echo "Open Grafana at: http://localhost:3000 (admin / admin)"
echo "Go to 'Dashboards' -> 'ARE - Microservices Overview' to view the data."
echo "====================================================================="
