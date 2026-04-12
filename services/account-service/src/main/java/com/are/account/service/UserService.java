package com.are.account.service;

import com.are.account.config.RabbitConfig;
import com.are.account.dto.UserRegistrationRequest;
import com.are.account.dto.UserResponse;
import com.are.account.dto.VerifyOnboardOtpRequest;
import com.are.account.repository.AccountRepository;
import com.are.account.repository.OTPRepository;
import com.are.account.repository.UserRepository;
import com.are.common.model.AccountEntity;
import com.are.common.model.AccountStatus;
import com.are.common.model.OTPRecipient;
import com.are.common.model.OTPType;
import com.are.common.model.OTPEntity;
import com.are.common.model.UserEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int OTP_EXPIRY_MINUTES = 10;

    private final UserRepository userRepository;
    private final OTPRepository otpRepository;
    private final AccountRepository accountRepository;
    private final RabbitTemplate rabbitTemplate;
    private final OnboardingWorkerService onboardingWorkerService;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
            OTPRepository otpRepository,
            AccountRepository accountRepository,
            RabbitTemplate rabbitTemplate,
            OnboardingWorkerService onboardingWorkerService) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.accountRepository = accountRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.onboardingWorkerService = onboardingWorkerService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Transactional
    public UserResponse registerUser(UserRegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("User already exists with email: " + request.email());
        }

        UserEntity user = new UserEntity();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());
        user.setStatus(com.are.common.model.UserStatus.INACTIVE);
        user = userRepository.save(user);

        createInactiveAccountForUser(user);
        String otp = createOnboardingOtp(user, request.phoneNumber());
        publishOnboardingOtpNotification(user, otp);

        log.info("Registered new user {} and created inactive account record", user.getEmail());
        return toResponse(user);
    }

    @Transactional
    public UserResponse verifyOnboardingOtp(VerifyOnboardOtpRequest request) {
        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + request.email()));

        OTPEntity otpEntity = otpRepository.findByUserAndOtpAndTypeAndUsedAtIsNull(user, request.otp(), OTPType.ONBOARD)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired onboarding OTP"));

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Onboarding OTP has expired");
        }

        otpEntity.setUsedAt(LocalDateTime.now());
        otpRepository.save(otpEntity);

        user.setPassword(passwordEncoder.encode(request.password()));
        user.setStatus(com.are.common.model.UserStatus.ACTIVE);
        user = userRepository.save(user);

        onboardingWorkerService.completeOnboarding(user);

        log.info("Onboarding completed for user {}", user.getEmail());
        return toResponse(user);
    }

    private void createInactiveAccountForUser(UserEntity user) {
        AccountEntity account = new AccountEntity();
        account.setUser(user);
        account.setAccountNumber(generateAccountNumber());
        account.setAccountName(user.getFirstName() + " " + user.getLastName());
        account.setBalance(BigDecimal.ZERO);
        account.setCurrency("NGN");
        account.setStatus(AccountStatus.INACTIVE);
        accountRepository.save(account);
    }

    private String createOnboardingOtp(UserEntity user, String phoneNumber) {
        OTPEntity otp = new OTPEntity();
        otp.setUser(user);
        otp.setOtp(generateOtp());
        otp.setType(OTPType.ONBOARD);
        otp.setRecipient(phoneNumber != null && !phoneNumber.isBlank() ? OTPRecipient.PHONE : OTPRecipient.EMAIL);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otp = otpRepository.save(otp);
        return otp.getOtp();
    }

    private void publishOnboardingOtpNotification(UserEntity user, String otp) {
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

    private String generateOtp() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }

    private String generateAccountNumber() {
        return "ARE" + String.valueOf(System.currentTimeMillis() % 1_000_000_000L);
    }

    private UserResponse toResponse(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getStatus());
    }
}
