package com.are.account.service;

import com.are.account.config.RabbitConfig;
import com.are.account.dto.UserResponse;

import com.are.account.repository.AccountRepository;
import com.are.account.repository.OTPRepository;
import com.are.account.repository.UserRepository;
import com.are.common.model.UserEntity;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final WebClient notificationWorkerWebClient;

    public UserService(UserRepository userRepository,
            OTPRepository otpRepository,
            AccountRepository accountRepository,
            WebClient notificationWorkerWebClient,
            OnboardingWorkerService onboardingWorkerService) {
        this.notificationWorkerWebClient = notificationWorkerWebClient;
    }

    @CircuitBreaker(name = "notificationWorkerCB", fallbackMethod = "publishOnboardingOtpNotificationFallback")
    void publishOnboardingOtpNotification(UserEntity user, String otp) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "ONBOARDING_OTP");
        event.put("email", user.getEmail());
        event.put("firstName", user.getFirstName());
        event.put("otp", otp);
        event.put("subject", "Your ARE onboarding OTP");
        event.put("body", "<p>Hello " + user.getFirstName() + ",</p>" +
                "<p>Your onboarding OTP is <strong>" + otp + "</strong>. It expires in 10 minutes.</p>");

        log.info("Calling notification-worker for OTP notification: {}", user.getEmail());
        
        notificationWorkerWebClient.post()
                .uri("/notifications/otp")
                .bodyValue(event)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    void publishOnboardingOtpNotificationFallback(UserEntity user, String otp, Throwable t) {
        log.error("Failed to call notification-worker for OTP (Circuit Breaker OPEN). Email: {}, Error: {}", 
                user.getEmail(), t.getMessage());
        // Potentially queue for later retry or notify admin
    }

    UserResponse toUserResponse(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getStatus());
    }
}
