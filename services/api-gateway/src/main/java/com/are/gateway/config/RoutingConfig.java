package com.are.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoutingConfig {

        @Bean
        public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
                return builder.routes()
                                // OpenAPI Docs Routes
                                .route("account-api-docs", r -> r
                                                .path("/v3/api-docs/account")
                                                .filters(f -> f.rewritePath("/v3/api-docs/account", "/v3/api-docs"))
                                                .uri("http://account-service:8082"))
                                .route("payment-api-docs", r -> r
                                                .path("/v3/api-docs/payment")
                                                .filters(f -> f.rewritePath("/v3/api-docs/payment", "/v3/api-docs"))
                                                .uri("http://payment-service:8081"))
                                .route("notification-api-docs", r -> r
                                                .path("/v3/api-docs/notification")
                                                .filters(f -> f.rewritePath("/v3/api-docs/notification",
                                                                "/v3/api-docs"))
                                                .uri("http://notification-worker:8083"))

                                // API Routes
                                .route("payment-route", r -> r
                                                .path("/api/payments/**")
                                                .filters(f -> f.stripPrefix(1))
                                                .uri("http://payment-service:8081"))
                                .route("account-route", r -> r
                                                .path("/api/accounts/**")
                                                .filters(f -> f.stripPrefix(1))
                                                .uri("http://account-service:8082"))
                                .route("transaction-route", r -> r
                                                .path("/api/transactions/**")
                                                .filters(f -> f.stripPrefix(1))
                                                .uri("http://account-service:8082"))
                                .build();
        }
}
