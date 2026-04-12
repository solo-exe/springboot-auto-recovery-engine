package com.are.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VerifyOtpRequest(
        @NotNull(message = "User ID is required") Long userId,
        @NotBlank(message = "OTP is required") String otp) {
}
