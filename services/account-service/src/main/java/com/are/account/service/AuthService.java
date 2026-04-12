package com.are.account.service;

import com.are.account.dto.*;
import com.are.account.repository.AccountRepository;
import com.are.account.repository.OTPRepository;
import com.are.account.repository.UserRepository;
import com.are.common.exception.ConflictException;
import com.are.common.exception.ForbiddenException;
import com.are.common.exception.ResourceNotFoundException;
import com.are.common.exception.UnauthorizedException;
import com.are.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       AccountRepository accountRepository,
                       OTPRepository otpRepository,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.otpRepository = otpRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Check email uniqueness
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("User already exists with email: " + request.email());
        }

        // Create user
        UserEntity user = new UserEntity();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPhoneNumber(request.phoneNumber());
        user.setType(UserType.CUSTOMER);
        user.setStatus(UserStatus.INACTIVE);
        user = userRepository.save(user);

        // Create account with status PENDING
        AccountEntity account = new AccountEntity();
        account.setUser(user);
        account.setAccountNumber(generateAccountNumber());
        account.setAccountName(user.getFirstName() + " " + user.getLastName());
        account.setBalance(BigDecimal.ZERO);
        account.setCurrency("NGN");
        account.setStatus(AccountStatus.PENDING);
        account = accountRepository.save(account);

        // Generate OTP
        String otpCode = generateOtp();
        OTPEntity otp = new OTPEntity();
        otp.setUser(user);
        otp.setOtp(otpCode);
        otp.setType(OTPType.ACCOUNT_ACTIVATION);
        otp.setRecipient(OTPRecipient.EMAIL);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otpRepository.save(otp);

        // Log OTP (no real email service per spec)
        log.info("OTP for {}: {}", user.getEmail(), otpCode);

        return new RegisterResponse(
                user.getId(),
                account.getAccountNumber(),
                "Registration successful. Check logs for OTP."
        );
    }

    @Transactional
    public void verifyOtp(VerifyOtpRequest request) {
        UserEntity user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.userId()));

        OTPEntity otp = otpRepository
                .findTopByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(user, OTPType.ACCOUNT_ACTIVATION)
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

        // Activate the account
        AccountEntity account = accountRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for user: " + request.userId()));
        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);

        log.info("Account activated for user {}", user.getEmail());
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
                jwtService.getExpirationSeconds()
        );
    }

    private String generateOtp() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }

    private String generateAccountNumber() {
        return String.format("%010d", System.nanoTime() % 10_000_000_000L);
    }
}
