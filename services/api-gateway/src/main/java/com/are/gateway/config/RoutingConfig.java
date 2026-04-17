package com.are.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoutingConfig {

    @org.springframework.beans.factory.annotation.Value("${services.account-uri}")
    private String accountUri;

    @org.springframework.beans.factory.annotation.Value("${services.payment-uri}")
    private String paymentUri;

    @org.springframework.beans.factory.annotation.Value("${services.notification-uri}")
    private String notificationUri;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Auth routes (public — no JWT required)
                .route("account-auth-route", r -> r
                        .path("/api/accounts/auth/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri(accountUri))
                // Notification routes (public)
                .route("notification-route", r -> r
                        .path("/notifications/**")
                        .uri(notificationUri))

                // Internal routes (no JWT — inter-service only)
                .route("internal-route", r -> r
                        .path("/internal/**")
                        .uri(accountUri))

                // Fault injection routes (public — no JWT)
                .route("account-fault-route", r -> r
                        .path("/fault/account/**")
                        .filters(f -> f.rewritePath("/fault/account/(?<path>.*)",
                                "/fault/${path}"))
                        .uri(accountUri))
                .route("payment-fault-route", r -> r
                        .path("/fault/payment/**")
                        .filters(f -> f.rewritePath("/fault/payment/(?<path>.*)",
                                "/fault/${path}"))
                        .uri(paymentUri))
                .route("notification-fault-route", r -> r
                        .path("/fault/notification/**")
                        .filters(f -> f.rewritePath("/fault/notification/(?<path>.*)",
                                "/fault/${path}"))
                        .uri(notificationUri))

                // _______________________________________________________________________
                // Profile routes (JWT required)
                .route("account-profile-route", r -> r
                        .path("/api/accounts/me/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri(accountUri))

                // Account management routes (JWT required)
                .route("account-route", r -> r
                        .path("/api/accounts/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri(accountUri))

                // Payment routes (JWT required)
                .route("payment-route", r -> r
                        .path("/api/payments/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri(paymentUri))

                // Swagger API Docs routes (proxied for unified Swagger UI)
                .route("account-api-docs", r -> r
                        .path("/v3/api-docs/account/**")
                        .uri(accountUri))
                .route("payment-api-docs", r -> r
                        .path("/v3/api-docs/payment/**")
                        .uri(paymentUri))
                .route("notification-api-docs", r -> r
                        .path("/v3/api-docs/notification/**")
                        .uri(notificationUri))
                .build();
    }
}
