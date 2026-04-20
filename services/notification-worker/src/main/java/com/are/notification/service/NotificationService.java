package com.are.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes payment notifications and sends email alerts.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ENTRIES = 50;

    private final JavaMailSender mailSender;
    private final ConcurrentLinkedDeque<Map<String, Object>> recentNotifications = new ConcurrentLinkedDeque<>();
    private final AtomicInteger count = new AtomicInteger(0);

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            log.info("Attempting to send email to {}", to);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("auto-recovery-engine@are.com");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            log.info("Email successfully sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    public void processNotification(Map<String, Object> event) {
        if (event.containsKey("otp")) {
            processOtpNotification(event);
        } else if (event.containsKey("reference") || event.containsKey("amount")) {
            processPaymentNotification(event);
        } else {
            log.warn("Unknown notification event type: {}", event);
        }
    }

    public void processOtpNotification(Map<String, Object> event) {
        Object otp = event.getOrDefault("otp", "0");
        String correlationId = String.valueOf(event.getOrDefault("correlationId", ""));
        String customerEmail = (String) event.get("email");

        log.info("OTP notification event received: otp={}, email={}",
                otp, customerEmail);

        // Send email if recipient exists
        if (customerEmail != null && !customerEmail.isBlank()) {
            sendOtpEmail(customerEmail, otp, correlationId);
        } else {
            log.warn("No customer email found in event for OTP. Skipping email delivery.");
        }

        // Build notification entry for dashboard
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", count.incrementAndGet());
        entry.put("type", "OTP");
        entry.put("otp", otp);
        entry.put("correlationId", correlationId);
        entry.put("customerEmail", customerEmail);
        entry.put("processedAt", LocalDateTime.now().toString());
        entry.putAll(event);

        recentNotifications.addFirst(entry);
        while (recentNotifications.size() > MAX_ENTRIES) {
            recentNotifications.removeLast();
        }
    }

    public void processPaymentNotification(Map<String, Object> event) {
        String reference = String.valueOf(event.getOrDefault("reference", "unknown"));
        Object amountObj = event.getOrDefault("amount", "0");
        String destination = String.valueOf(event.getOrDefault("destination", "unknown"));
        String correlationId = String.valueOf(event.getOrDefault("correlationId", ""));
        String customerEmail = (String) event.get("email");

        log.info("Payment notification event received: reference={}, amount={}, email={}",
                reference, amountObj, customerEmail);

        // Send email if recipient exists
        if (customerEmail != null && !customerEmail.isBlank()) {
            BigDecimal amount = new BigDecimal(amountObj.toString());
            sendPaymentEmail(customerEmail, reference, amount, destination);
        } else {
            log.warn("No customer email found in event for reference {}. Skipping email delivery.", reference);
        }

        // Build notification entry for dashboard
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", count.incrementAndGet());
        entry.put("type", "PAYMENT");
        entry.put("reference", reference);
        entry.put("amount", amountObj);
        entry.put("destinationAccountNumber", destination);
        entry.put("correlationId", correlationId);
        entry.put("customerEmail", customerEmail);
        entry.put("processedAt", LocalDateTime.now().toString());
        entry.putAll(event);

        recentNotifications.addFirst(entry);
        while (recentNotifications.size() > MAX_ENTRIES) {
            recentNotifications.removeLast();
        }
    }

    private void sendOtpEmail(String to, Object otp, String correlationId) {
        String subject = "Your OTP Code: " + otp;
        String body = "Dear Customer,\n\n" +
                "Your One-Time Password (OTP) is: " + otp + "\n\n" +
                "This code expires in 10 minutes.\n\n" +
                "Correlation ID: " + correlationId + "\n" +
                "Thank you for choosing ARE.";
        sendEmail(to, subject, body);
    }

    private void sendPaymentEmail(String to, String reference, BigDecimal amount, String destination) {
        String subject = "Transaction Alert: [ARE-" + reference + "]";
        String body = "Dear Customer,\n\n" +
                "A payment of NGN " + amount + " has been successfully processed from your account.\n\n" +
                "Details:\n" +
                "- Reference: " + reference + "\n" +
                "- Destination: " + destination + "\n" +
                "- Date: " + LocalDateTime.now() + "\n\n" +
                "Thank you for banking with Auto-Recovery Enterprises.";
        sendEmail(to, subject, body);
    }

    public List<Map<String, Object>> getRecentNotifications() {
        return new ArrayList<>(recentNotifications);
    }
}
