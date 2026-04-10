package com.are.gateway.health;

import javax.sql.DataSource;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthIndicatorConfig {

    private final DataSource dataSource;
    private final RabbitTemplate rabbitTemplate;

    public HealthIndicatorConfig(DataSource dataSource, RabbitTemplate rabbitTemplate) {
        this.dataSource = dataSource;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Bean("db")
    public HealthIndicator dbHealthIndicator() {
        return () -> {
            try {
                // Attempt to get connection from the injected DataSource
                var connection = dataSource.getConnection();
                boolean isValid = connection.isValid(5); // Check validity within 5 seconds
                connection.close();

                if (isValid) {
                    return Health.up()
                            .withDetail("database", "PostgreSQL")
                            .withDetail("validationQuery", "isValid()")
                            .build();
                } else {
                    return Health.down()
                            .withDetail("database", "PostgreSQL")
                            .withDetail("error", "Connection validation failed")
                            .build();
                }
            } catch (Exception e) {
                return Health.down()
                        .withDetail("database", "PostgreSQL")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    @Bean("rabbit")
    public HealthIndicator rabbitHealthIndicator() {
        return () -> {
            // We use try-with-resources to ensure the connection closes automatically
            System.out.println("Connecting to RabbitMQ...");
            try (var connection = rabbitTemplate.getConnectionFactory().createConnection()) {
                return Health.up()
                        .withDetail("version", "4.0.0") // M4 Pro / 2026 standard
                        .withDetail("nodes", "rabbit@localhost")
                        .build();
            } catch (Exception e) {
                // Any connection failure in the 'try' or 'create' will land here
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }
}