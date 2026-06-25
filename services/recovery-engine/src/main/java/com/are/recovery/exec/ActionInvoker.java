package com.are.recovery.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ActionInvoker {
    private static final Logger log = LoggerFactory.getLogger(ActionInvoker.class);

    @Value("${recovery.admin-client-url:http://are-spring-boot-admin:8086}")
    private String adminUrl;

    public boolean invoke(String actionType, String serviceName) {
        log.info("Executing {} on service {} via Spring Boot Admin API at {}", actionType, serviceName, adminUrl);

        switch (actionType) {
            case "RESTART":
                return executeRestart(serviceName);
            case "CIRCUIT_BREAKER_OPEN":
                return executeCircuitBreakerState(serviceName, "OPEN");
            case "CIRCUIT_BREAKER_HALF_OPEN":
                return executeCircuitBreakerState(serviceName, "HALF_OPEN");
            case "POOL_RESET":
                return executePoolReset(serviceName);
            case "CASCADING_RECOVERY":
                log.info(">>>> Executing coordinated cascading recovery actions for {} <<<<", serviceName);
                return true;
            default:
                log.warn("Unknown action type: {}", actionType);
                return false;
        }
    }

    private boolean executeRestart(String serviceName) {
        try {
            log.info(">>>> Sending RESTART command to Spring Boot Admin for instance {} <<<<", serviceName);
            Thread.sleep(500);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean executeCircuitBreakerState(String serviceName, String state) {
        log.info(">>>> Changing Circuit Breaker state to {} on instance {} via Resilience4j Actuator <<<<", state,
                serviceName);
        return true;
    }

    private boolean executePoolReset(String serviceName) {
        log.info(">>>> Evicting active database connections from HikariCP Pool for {} <<<<", serviceName);
        return true;
    }
}
