package com.are.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter to extract X-User-Id and X-User-Role headers (injected by API Gateway)
 * and populate the UserContext for the current thread.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserContextFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        String userIdStr = httpRequest.getHeader("X-User-Id");
        String userRole = httpRequest.getHeader("X-User-Role");

        try {
            if (userIdStr != null && !userIdStr.isBlank()) {
                UserContext.setUserId(Long.valueOf(userIdStr));
                UserContext.setUserRole(userRole != null ? userRole : "CUSTOMER");
                log.debug("UserContext populated for User ID: {}", userIdStr);
            } else {
                log.debug("No X-User-Id header found in request to {}", httpRequest.getRequestURI());
            }
            
            chain.doFilter(request, response);
        } finally {
            // Very important to clear ThreadLocal to prevent memory leaks and info leakage
            UserContext.clear();
        }
    }
}
