package com.are.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank(message = "Owner name is required") String ownerName,

        @NotNull(message = "Currency is required") @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code") String currency,

        @DecimalMin(value = "0.00", message = "Initial balance cannot be negative") BigDecimal initialBalance,

        String email,

        String phoneNumber) {
}
