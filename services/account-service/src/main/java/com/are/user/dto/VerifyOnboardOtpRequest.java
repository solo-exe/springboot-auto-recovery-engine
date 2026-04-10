package com.are.user.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyOnboardOtpRequest(
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "OTP is required") String otp,
        @NotBlank(message = "Password is required") String password) {
}
