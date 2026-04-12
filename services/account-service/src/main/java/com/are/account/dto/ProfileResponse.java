package com.are.account.dto;

import com.are.common.model.AccountStatus;
import com.are.common.model.UserStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProfileResponse(
        Long userId,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        UserStatus userStatus,
        String role,
        Long accountId,
        String accountNumber,
        BigDecimal balance,
        String currency,
        AccountStatus accountStatus,
        LocalDateTime createdAt) {
}
