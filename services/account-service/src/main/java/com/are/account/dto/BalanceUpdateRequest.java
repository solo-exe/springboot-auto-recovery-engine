package com.are.account.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BalanceUpdateRequest(
        @NotNull(message = "Amount is required") BigDecimal amount) {
}
