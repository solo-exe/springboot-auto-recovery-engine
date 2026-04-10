package com.are.user.controller;

import com.are.user.dto.UserRegistrationRequest;
import com.are.user.dto.UserResponse;
import com.are.user.dto.VerifyOnboardOtpRequest;
import com.are.user.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(@RequestBody UserRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.registerUser(request));
    }

    @PostMapping("/create-password")
    public ResponseEntity<UserResponse> completeOnboarding(@RequestBody VerifyOnboardOtpRequest request) {
        return ResponseEntity.ok(userService.verifyOnboardingOtp(request));
    }
}
