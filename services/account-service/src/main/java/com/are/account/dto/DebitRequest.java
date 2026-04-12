package com.are.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DebitRequest(
        @NotNull(message = "Account ID is required") Long accountId,
        @NotNull(message = "Amount is required") @DecimalMin(value = "0.01", message = "Amount must be positive") BigDecimal amount,
        @NotBlank(message = "Payment reference is required") String paymentReference) {
}
