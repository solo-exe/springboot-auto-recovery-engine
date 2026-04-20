package com.are.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Common OpenAPI configuration for all microservices.
 * Provides standardized Bearer JWT authentication scheme and service-specific
 * API metadata.
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Bean
    public OpenAPI commonOpenAPI() {
        String title = getServiceTitle();
        String description = getServiceDescription();
        String serverUrl = getGatewayServerUrl();

        return new OpenAPI()
                .servers(List.of(
                        new Server().url(serverUrl).description("API Gateway")))
                .info(new Info()
                        .title(title)
                        .description(description)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name(title + " Team")
                                .email(serviceName.replace("-", "") + "-team@are.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter your JWT token in the format: <token>")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
    }

    private String getServiceTitle() {
        switch (serviceName) {
            case "account-service":
                return "Account Service API";
            case "payment-service":
                return "Payment Service API";
            case "notification-worker":
                return "Notification Worker API";
            case "api-gateway":
                return "API Gateway";
            default:
                return serviceName + " API";
        }
    }

    private String getServiceDescription() {
        switch (serviceName) {
            case "account-service":
                return "Account Service - Manages user accounts and profile information for the Auto-Recovery Engine";
            case "payment-service":
                return "Payment Service - Handles payment processing and transaction management";
            case "notification-worker":
                return "Notification Worker - Processes and sends notifications for the Auto-Recovery Engine";
            case "api-gateway":
                return "API Gateway - Entry point and routing for all microservices";
            default:
                return serviceName + " - Microservice for the Auto-Recovery Engine";
        }
    }

    private String getGatewayServerUrl() {
        switch (serviceName) {
            case "account-service":
                return "http://localhost:8080/api/accounts";
            case "payment-service":
                return "http://localhost:8080/api/payments";
            case "notification-worker":
                return "http://localhost:8080/notifications";
            case "api-gateway":
                return "http://localhost:8080";
            default:
                return "http://localhost:8080";
        }
    }
}
