package com.are.notification.listener;

import com.are.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @RabbitListener(queues = "notification.queue")
    public void handlePaymentNotification(Map<String, Object> event) {
        log.info("Received payment notification event: paymentId={}, status={}",
                event.get("paymentId"), event.get("status"));

        try {
            notificationService.processNotification(event);
        } catch (Exception e) {
            log.error("Failed to process notification: {}", e.getMessage(), e);
            throw e; // Let RabbitMQ retry
        }
    }
}
