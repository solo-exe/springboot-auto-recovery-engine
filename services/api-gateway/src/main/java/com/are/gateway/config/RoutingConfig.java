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
