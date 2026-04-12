package com.are.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT validation filter for the API Gateway.
 * Validates tokens issued by account-service.
 * On success, adds X-User-Id and X-User-Role headers to downstream requests.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final SecretKey secretKey;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/accounts/auth/",
            "/internal/",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui",
            "/webjars",
            "/fault/",
            "/notifications/"
    );

    public JwtAuthFilter(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Allow public paths without authentication
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                return chain.filter(exchange);
            }
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            // Add user info to request headers for downstream services
            exchange = exchange.mutate()
                    .request(r -> r
                            .header("X-User-Id", userId)
                            .header("X-User-Role", role != null ? role : "CUSTOMER"))
                    .build();

            return chain.filter(exchange);
        } catch (Exception e) {
            return unauthorizedResponse(exchange, "Invalid or expired token");
        }
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"data\":null,\"message\":\"" + message + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -100; // Run before routing, after CorrelationIdFilter
    }
}
