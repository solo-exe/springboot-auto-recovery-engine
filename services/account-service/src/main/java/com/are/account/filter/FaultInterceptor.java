package com.are.account.filter;

import com.are.account.controller.FaultSimulationController;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Intercepts business requests and applies fault simulation effects.
 * Skips /fault/**, /actuator/**, /internal/**, /swagger-ui/**, /v3/api-docs/** paths.
 */
@Component
public class FaultInterceptor implements Filter {

    private final FaultSimulationController faultController;

    public FaultInterceptor(FaultSimulationController faultController) {
        this.faultController = faultController;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // Skip non-business paths
        if (path.startsWith("/fault") || path.startsWith("/actuator") ||
            path.startsWith("/internal") || path.startsWith("/swagger-ui") ||
            path.startsWith("/v3/api-docs")) {
            chain.doFilter(request, response);
            return;
        }

        // Simulate unresponsive service (60s sleep)
        if (faultController.isUnresponsive()) {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Simulate error rate
        int errorRate = faultController.getErrorRate();
        if (errorRate > 0 && ThreadLocalRandom.current().nextInt(100) < errorRate) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                    "{\"success\":false,\"data\":null,\"message\":\"Simulated fault: error-rate=" + errorRate + "%\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
