package com.are.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
                @NotNull(message = "User ID is required") Long userId,
                @NotBlank(message = "OTP is required") String otp,
                @Size(max = 20, message = "Phone number cannot exceed 20 characters") String phoneNumber,
                @Email(message = "Input must be a valid emial") String email) {
}
