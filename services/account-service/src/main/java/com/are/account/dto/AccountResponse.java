package com.are.account.dto;

import com.are.common.model.AccountStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String accountNumber,
        String ownerName,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        String email,
        String phoneNumber,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
