package com.are.payment.dto;

import com.are.common.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String description,
        String reference,
        String correlationId,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
