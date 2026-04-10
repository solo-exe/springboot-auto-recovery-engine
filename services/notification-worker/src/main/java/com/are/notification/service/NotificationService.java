package com.are.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public NotificationService(JavaMailSender mailSender,
            @Value("${spring.mail.username:donotreply@are.example}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void processNotification(Map<String, Object> event) {
        String type = String.valueOf(event.get("type"));

        switch (type) {
            case "ONBOARDING_OTP" -> sendOnboardingOtp(event);
            case "ONBOARDING_COMPLETE" -> sendOnboardingComplete(event);
            case "PAYMENT" -> sendPaymentNotification(event);
            default -> log.info("[NOTIFICATION] Unsupported event type: {}", type);
        }
    }

    private void sendOnboardingOtp(Map<String, Object> event) {
        String email = String.valueOf(event.get("email"));
        String firstName = String.valueOf(event.get("firstName"));
        String otp = String.valueOf(event.get("otp"));
        String subject = String.valueOf(event.getOrDefault("subject", "Complete your onboarding"));
        String body = String.valueOf(event.getOrDefault("body",
                "Hello " + firstName + ",<br><br>Your onboarding OTP is <strong>" + otp + "</strong>. " +
                        "Please use it within the next 10 minutes to complete your registration."));

        sendEmail(email, subject, body);
    }

    private void sendOnboardingComplete(Map<String, Object> event) {
        String email = String.valueOf(event.get("email"));
        String firstName = String.valueOf(event.get("firstName"));
        String accountNumber = String.valueOf(event.get("accountNumber"));
        String subject = String.valueOf(event.getOrDefault("subject", "Your account is ready"));
        String body = String.valueOf(event.getOrDefault("body",
                "Hello " + firstName + ",<br><br>Congratulations! Your account " + accountNumber + " is now active."));

        sendEmail(email, subject, body);
    }

    private void sendPaymentNotification(Map<String, Object> event) {
        String status = String.valueOf(event.get("status"));
        Object paymentId = event.get("paymentId");
        Object amount = event.get("amount");
        String currency = String.valueOf(event.get("currency"));
        String correlationId = String.valueOf(event.get("correlationId"));
        String email = String.valueOf(event.getOrDefault("email", "")).trim();

        String subject = "Payment notification";
        String body = switch (status) {
            case "COMPLETED" -> "Payment " + paymentId + " completed — " + amount + " " + currency +
                    " transferred successfully. CorrelationId: " + correlationId;
            case "FAILED" -> "Payment " + paymentId + " failed. CorrelationId: " + correlationId;
            case "REFUNDED" -> "Payment " + paymentId + " refunded — " + amount + " " + currency +
                    " returned. CorrelationId: " + correlationId;
            default -> "Payment " + paymentId + " status: " + status;
        };

        if (email.isBlank()) {
            log.info("[NOTIFICATION] Payment event without email: subject='{}', body='{}'", subject, body);
            return;
        }

        sendEmail(email, subject, body);
    }

    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Sent notification email to {} with subject '{}'.", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to build email message for {}: {}", to, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send notification email to {}: {}", to, e.getMessage(), e);
        }
    }
}
