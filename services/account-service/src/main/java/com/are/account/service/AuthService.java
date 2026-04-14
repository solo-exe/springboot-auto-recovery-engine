package com.are.account.service;

import com.are.account.dto.*;
import com.are.account.repository.AccountRepository;
import com.are.account.repository.OTPRepository;
import com.are.account.repository.UserRepository;
import com.are.common.exception.ConflictException;
// import com.are.common.exception.ConflictException;
import com.are.common.exception.ForbiddenException;
import com.are.common.exception.ResourceNotFoundException;
import com.are.common.exception.UnauthorizedException;
import com.are.common.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// import org.springframework.https
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
// import java.math.BigDecimal;
import java.time.LocalDateTime;
// import java.util.Optional;
// import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles user registration, OTP verification, and login.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int OTP_EXPIRY_MINUTES = 10;

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final OTPRepository otpRepository;
    private final JwtService jwtService;
    private final AccountService accountService;
    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final OnboardingWorkerService onboardingWorkerService;

    public AuthService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            OTPRepository otpRepository,
            AccountService accountService,
            UserService userService,
            JwtService jwtService,
            OnboardingWorkerService onboardingWorkerService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.otpRepository = otpRepository;
        this.jwtService = jwtService;
        this.accountService = accountService;
        this.userService = userService;
        this.onboardingWorkerService = onboardingWorkerService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Transactional
    public OtpVerifyResponse verifyOtp(VerifyOtpRequest request) {
        UserEntity user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.userId()));

        OTPEntity otp = otpRepository
                .findTopByUserAndEmailAndUsedAtIsNullAndDeletedIsFalseOrderByCreatedAtDesc(user, request.email())
                .orElseThrow(() -> new IllegalArgumentException("No pending OTP found for this user"));

        if (!otp.getOtp().equals(request.otp())) {
            throw new IllegalArgumentException("Invalid OTP code");
        }

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP has expired");
        }

        // Mark OTP as used
        otp.setUsedAt(LocalDateTime.now());
        otpRepository.save(otp);

        // Activate the user
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        if (otp.getType() == OTPType.ONBOARD) {
            // Activate the account only if it's inactive and needs activation
            AccountEntity account = accountRepository.findByUser(user)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inactive account not found for user: " + request.userId()));

            if (account.getStatus() != AccountStatus.ACTIVE) {
                account.setStatus(AccountStatus.ACTIVE);
                accountRepository.save(account);
                log.info("Account activated for user {}", user.getEmail());
            } else {
                log.info("Account for user {} is already active, no action taken.", user.getEmail());
            }
        }

        return new OtpVerifyResponse(user.getId(), otp.getId());
    }

    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        // Check account is activated
        AccountEntity account = accountRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenException("Account not activated");
        }

        String role = user.getType().name();
        String token = jwtService.generateToken(user.getId(), user.getEmail(), role);

        log.info("User {} logged in successfully", user.getEmail());
        return new LoginResponse(
                token,
                user.getId(),
                user.getEmail(),
                role,
                jwtService.getExpirationSeconds());
    }

    @Transactional
    public UserResponse createPassword(VerifyOnboardOtpRequest request) {
        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + request.email()));

        OTPEntity otpEntity = otpRepository.findById(request.confirmationId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid confirmation ID"));
        if (otpEntity.getType() != OTPType.ONBOARD) {
            throw new ConflictException("Invalid Confirmation Id");
        }

        user.setPassword(passwordEncoder.encode(request.password()));
        user.setStatus(com.are.common.model.UserStatus.ACTIVE);
        user = userRepository.save(user);

        onboardingWorkerService.completeOnboarding(user);

        log.info("Onboarding completed for user {}", user.getEmail());
        return userService.toUserResponse(user);
    }

    @Transactional
    public UserResponse registerUser(UserRegistrationRequest request) {
        // Block if user already completed registration
        if (userRepository.existsByEmailAndPasswordIsNotNull(request.email())) {
            throw new ConflictException("User already exists with email: " + request.email());
        }

        // Fetch existing incomplete registration (email exists but no password yet)
        UserEntity user = userRepository.findByEmail(request.email())
                .orElse(new UserEntity());

        // Populate or update fields
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());
        user.setType(UserType.CUSTOMER);
        user.setStatus(UserStatus.INACTIVE);

        user = userRepository.save(user);

        if (!accountRepository.existsByUserId(user.getId())) {
            createInactiveAccountForUser(user);
        }

        String otp = createOnboardingOtp(user, request.phoneNumber(), request.email());
        this.userService.publishOnboardingOtpNotification(user, otp);

        log.info("Registered new user {} and created inactive account record", user.getEmail());
        return userService.toUserResponse(user);
    }

    private void createInactiveAccountForUser(UserEntity user) {
        AccountEntity account = new AccountEntity();
        account.setUser(user);
        account.setAccountNumber(this.accountService.generateAccountNumber());
        account.setAccountName(user.getFirstName() + " " + user.getLastName());
        account.setBalance(BigDecimal.ZERO);
        account.setCurrency("NGN");
        account.setStatus(AccountStatus.INACTIVE);
        accountRepository.save(account);
    }

    private String createOnboardingOtp(UserEntity user, String phoneNumber, String email) {
        OTPEntity otp = new OTPEntity();
        otp.setUser(user);
        otp.setOtp(generateOtp());
        otp.setType(OTPType.ONBOARD);
        otp.setRecipient(email != null && !email.isBlank() ? OTPRecipient.EMAIL : OTPRecipient.PHONE);
        otp.setEmail(email);
        otp.setPhoneNumber(phoneNumber);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otp = otpRepository.save(otp);
        return otp.getOtp();
    }

    private String generateOtp() {
        String output = String.format("%06d", ThreadLocalRandom.current().nextInt(0,
                1_000_000));
        System.out.println("THE GENERATED OTP" + output);
        return output;
    }

    // private String generateAccountNumber() {
    // return String.format("%010d", System.nanoTime() % 10_000_000_000L);
    // }

    // @Transactional
    // public RegisterResponse register(RegisterRequest request) {

    // System.out.println("THE REQUEST IS " + request);

    // if (userRepository.existsByEmailAndPasswordIsNotNull(request.email())) {
    // throw new ConflictException("User already exists with email: " +
    // request.email());
    // }

    // UserEntity user = userRepository.findByEmail(request.email())
    // .orElse(new UserEntity());

    // // Populate or update fields
    // user.setFirstName(request.firstName());
    // user.setLastName(request.lastName());
    // user.setEmail(request.email());
    // user.setPassword(passwordEncoder.encode(request.password()));
    // user.setPhoneNumber(request.phoneNumber());
    // user.setType(UserType.CUSTOMER);
    // user.setStatus(UserStatus.INACTIVE);

    // user = userRepository.save(user);

    // // Create account with status PENDING
    // AccountEntity account = new AccountEntity();
    // account.setUser(user);
    // account.setAccountNumber(generateAccountNumber());
    // account.setAccountName(user.getFirstName() + " " + user.getLastName());
    // account.setBalance(BigDecimal.ZERO);
    // account.setCurrency("NGN");
    // account.setStatus(AccountStatus.PENDING);
    // account = accountRepository.save(account);

    // // Generate OTP
    // String otpCode = generateOtp();
    // OTPEntity otp = new OTPEntity();
    // otp.setUser(user);
    // otp.setOtp(otpCode);
    // otp.setType(OTPType.ACCOUNT_ACTIVATION);
    // otp.setEmail(user.getEmail());
    // otp.setRecipient(OTPRecipient.EMAIL);
    // otp.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
    // otpRepository.save(otp);

    // // Log OTP (no real email service per spec)
    // log.info("OTP for {}: {}", user.getEmail(), otpCode);

    // return new RegisterResponse(
    // user.getId(),
    // account.getAccountNumber(),
    // "Registration successful. Check logs for OTP.");
    // }
}
