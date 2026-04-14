package com.are.account.service;

import com.are.account.config.RabbitConfig;
import com.are.account.dto.UserResponse;

import com.are.account.repository.AccountRepository;
import com.are.account.repository.OTPRepository;
import com.are.account.repository.UserRepository;
import com.are.common.model.UserEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int OTP_EXPIRY_MINUTES = 10;

    private final RabbitTemplate rabbitTemplate;

    public UserService(UserRepository userRepository,
            OTPRepository otpRepository,
            AccountRepository accountRepository,
            RabbitTemplate rabbitTemplate,
            OnboardingWorkerService onboardingWorkerService) {
        this.rabbitTemplate = rabbitTemplate;
    }

    void publishOnboardingOtpNotification(UserEntity user, String otp) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "ONBOARDING_OTP");
        event.put("email", user.getEmail());
        event.put("firstName", user.getFirstName());
        event.put("otp", otp);
        event.put("subject", "Your ARE onboarding OTP");
        event.put("body", "<p>Hello " + user.getFirstName() + ",</p>" +
                "<p>Your onboarding OTP is <strong>" + otp + "</strong>. It expires in 10 minutes.</p>");

        rabbitTemplate.convertAndSend(RabbitConfig.NOTIFICATION_EXCHANGE,
                RabbitConfig.NOTIFICATION_ROUTING_KEY, event);
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
