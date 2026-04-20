.PHONY: build start-gateway start-payment start-account start-notif start-recovery start-admin launch make-migrations migrate migrate-diff migrate-run migrate-rollback rollback infra-compose-up compose-up compose-down compose-logs clean

# Variables
MVN = mvn
COMMON = services/common-core

# Load environment variables from .env if it exists
ifneq (,$(wildcard ./.env))
    include .env
    export
endif

# ----------------------------------------
# Build & Clean Commands
# ----------------------------------------

# Build the entire project without running tests
build:
	$(MVN) clean install -DskipTests

# Clean all Maven build artifacts
clean:
	$(MVN) clean

# ----------------------------------------
# Liquibase Database Migration Commands
# ----------------------------------------

# Generate new migration files based on changes in your @Entity classes
migrate-diff:
	@echo "Checking Java entities against the database and generating SQL diff..."
	$(MVN) liquibase:diff -pl $(COMMON)

# Manually push all pending migrations to the database
migrate-run:
	@echo "Running all pending database migrations..."
	$(MVN) liquibase:update -pl $(COMMON)

# Revert the last N applied changesets (Default is 1)
# Note: Liquibase counts each table or constraint as 1 changeset.
# If a migration file has 11 changesets, you need COUNT=11
COUNT ?= 1
migrate-rollback:
	@echo "Rolling back the last $(COUNT) applied changesets..."
	$(MVN) liquibase:rollback -Dliquibase.rollbackCount=$(COUNT) -pl $(COMMON)

# ----------------------------------------
# Docker / Infrastructure Commands
# ----------------------------------------

infra-compose-up:
	docker-compose up -d postgres rabbitmq

infra-compose-down:
	docker-compose down postgres rabbitmq

# Start all infrastructure (Postgres, Prometheus, Grafana, etc.) in the background
compose-up:
	docker-compose up -d

# Stop and tear down all Docker infrastructure
compose-down:
	docker-compose down

# View logs for Docker infrastructure
compose-logs:
	docker-compose logs -f

# 🚀 Launch the entire system (Docker tiers + Java services in separate terminals)
launch:
	@chmod +x scripts/launch_system.sh
	@./scripts/launch_system.sh

# ----------------------------------------
# Local Microservice Run Commands 
# ----------------------------------------

# Run the API Gateway locally
start-gateway:
	$(MVN) spring-boot:run -pl services/api-gateway # 2>&1 | grep -v "WARNING:"

# Run the Payment Service locally
start-payment:
	$(MVN) spring-boot:run -pl services/payment-service # 2>&1 | grep -v "WARNING:"

# Run the Account Service locally
start-account:
	$(MVN) spring-boot:run -pl services/account-service # 2>&1 | grep -v "WARNING:"

# Run the Account Service locally
start-notif:
	$(MVN) spring-boot:run -pl services/notification-worker # 2>&1 | grep -v "WARNING:"

# Run the Recovery Engine locally
start-recovery:
	$(MVN) spring-boot:run -pl services/recovery-engine # 2>&1 | grep -v "WARNING:"

# Run the Spring Boot Admin locally
start-admin:
	$(MVN) spring-boot:run -pl services/spring-boot-admin # 2>&1 | grep -v "WARNING:"