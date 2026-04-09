package com.are.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /**
     * Processes a payment notification event.
     * In production, this would integrate with email/SMS providers.
     * For now, it logs the notification as a stub.
     */
    public void processNotification(Map<String, Object> event) {
        String status = String.valueOf(event.get("status"));
        Object paymentId = event.get("paymentId");
        Object amount = event.get("amount");
        String currency = String.valueOf(event.get("currency"));
        String correlationId = String.valueOf(event.get("correlationId"));

        switch (status) {
            case "COMPLETED" -> {
                log.info("[EMAIL STUB] Payment {} completed — £{} {} transferred successfully. CorrelationId: {}",
                        paymentId, amount, currency, correlationId);
                log.info("[SMS STUB] Your payment of {} {} has been processed. Ref: {}",
                        amount, currency, event.get("reference"));
            }
            case "FAILED" -> {
                log.warn("[EMAIL STUB] Payment {} failed. CorrelationId: {}", paymentId, correlationId);
                log.warn("[SMS STUB] Your payment could not be processed. Please try again.");
            }
            case "REFUNDED" -> {
                log.info("[EMAIL STUB] Payment {} refunded — {} {} returned. CorrelationId: {}",
                        paymentId, amount, currency, correlationId);
                log.info("[SMS STUB] Refund of {} {} has been processed.", amount, currency);
            }
            default -> log.info("[NOTIFICATION] Payment {} status: {}", paymentId, status);
        }
    }
}
