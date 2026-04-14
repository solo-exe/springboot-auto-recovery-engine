package com.are.account.controller;

import com.are.account.dto.*;
import com.are.account.service.AuthService;
import com.are.common.dto.ApiResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
// import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public authentication endpoints.
 * These do NOT require JWT — matched as public routes in api-gateway.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // @PostMapping("/register")
    // public ResponseEntity<ApiResponse<RegisterResponse>> register(
    // @Valid @RequestBody RegisterRequest request) {
    // RegisterResponse response = authService.register(request);
    // return ResponseEntity.status(HttpStatus.CREATED)
    // .body(ApiResponse.success(response, "Registration successful. Check logs for
    // OTP."));
    // }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<OtpVerifyResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        OtpVerifyResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Account activated successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signUp(@RequestBody UserRegistrationRequest request) {
        UserResponse response = authService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/create-password")
    public ResponseEntity<ApiResponse<UserResponse>> completeOnboarding(
            @RequestBody VerifyOnboardOtpRequest request) {
        UserResponse response = authService.createPassword(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
