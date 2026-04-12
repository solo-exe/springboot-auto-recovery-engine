package com.are.notification.listener;

import com.are.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationListener.class);

    private final NotificationService notificationService;

    public PaymentNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "are.notifications")
    public void handlePaymentNotification(Map<String, Object> event) {
        // Extract correlationId from event and store in MDC
        String correlationId = String.valueOf(event.getOrDefault("correlationId", ""));
        if (!correlationId.isBlank()) {
            MDC.put("correlationId", correlationId);
        }
        MDC.put("serviceName", "notification-worker");

        log.info("Received payment notification event: reference={}, amount={}",
                event.get("reference"), event.get("amount"));

        try {
            notificationService.processNotification(event);
        } catch (Exception e) {
            log.error("Failed to process notification: {}", e.getMessage(), e);
            throw e;
        } finally {
            MDC.clear();
        }
    }
}
