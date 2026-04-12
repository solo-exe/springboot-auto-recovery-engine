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
                                // Auth routes (public — no JWT required)
                                .route("account-auth-route", r -> r
                                                .path("/api/accounts/auth/**")
                                                .filters(f -> f.stripPrefix(1))
                                                .uri("http://localhost:8082"))

                                // Account routes (JWT required)
                                .route("account-route", r -> r
                                                .path("/api/accounts/**")
                                                .filters(f -> f.stripPrefix(1))
                                                .uri("http://localhost:8082"))

                                // Payment routes (JWT required)
                                .route("payment-route", r -> r
                                                .path("/api/payments/**")
                                                .filters(f -> f.stripPrefix(1))
                                                .uri("http://localhost:8081"))

                                // Internal routes (no JWT — inter-service only)
                                .route("internal-route", r -> r
                                                .path("/internal/**")
                                                .uri("http://localhost:8082"))

                                // Notification routes (public)
                                .route("notification-route", r -> r
                                                .path("/notifications/**")
                                                .uri("http://localhost:8085"))

                                // Fault injection routes (public — no JWT)
                                .route("account-fault-route", r -> r
                                                .path("/fault/account/**")
                                                .filters(f -> f.rewritePath("/fault/account/(?<path>.*)", "/fault/${path}"))
                                                .uri("http://localhost:8082"))
                                .route("payment-fault-route", r -> r
                                                .path("/fault/payment/**")
                                                .filters(f -> f.rewritePath("/fault/payment/(?<path>.*)", "/fault/${path}"))
                                                .uri("http://localhost:8081"))
                                .route("notification-fault-route", r -> r
                                                .path("/fault/notification/**")
                                                .filters(f -> f.rewritePath("/fault/notification/(?<path>.*)", "/fault/${path}"))
                                                .uri("http://localhost:8085"))

                                // Swagger UI routes
                                .route("account-swagger-ui", r -> r
                                                .path("/swagger-ui/account/**")
                                                .filters(f -> f.rewritePath("/swagger-ui/account/(?<path>.*)",
                                                                "/swagger-ui/${path}"))
                                                .uri("http://localhost:8082"))
                                .route("payment-swagger-ui", r -> r
                                                .path("/swagger-ui/payment/**")
                                                .filters(f -> f.rewritePath("/swagger-ui/payment/(?<path>.*)",
                                                                "/swagger-ui/${path}"))
                                                .uri("http://localhost:8081"))
                                .build();
        }
}
