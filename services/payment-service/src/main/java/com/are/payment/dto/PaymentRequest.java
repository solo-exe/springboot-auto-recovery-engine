package com.are.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull(message = "From account ID is required") Long fromAccountId,

        @NotNull(message = "To account ID is required") Long toAccountId,

        @NotNull(message = "Amount is required") @DecimalMin(value = "0.01", message = "Amount must be greater than zero") BigDecimal amount,

        @NotNull(message = "Currency is required") @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code") String currency,

        String description) {
}
