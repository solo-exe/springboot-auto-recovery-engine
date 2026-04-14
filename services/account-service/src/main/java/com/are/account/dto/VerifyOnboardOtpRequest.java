package com.are.account.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyOnboardOtpRequest(
                @NotBlank(message = "Email is required") String email,
                @NotBlank(message = "OTP confirmation ID is required") Long confirmationId,
                @NotBlank(message = "Password is required") String password) {
}
