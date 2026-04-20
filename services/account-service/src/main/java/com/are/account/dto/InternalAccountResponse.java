package com.are.account.dto;

import com.are.common.model.AccountStatus;
import java.math.BigDecimal;

public record InternalAccountResponse(
        Long accountId,
        String accountNumber,
        BigDecimal balance,
        AccountStatus status,
        String email) {
}
