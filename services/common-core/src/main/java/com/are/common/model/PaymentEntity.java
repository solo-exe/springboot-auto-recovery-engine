package com.are.common.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "payments")
public class PaymentEntity extends BaseEntity {

    @Column(nullable = false)
    private Long fromAccountId;

    @Column(nullable = false)
    private Long toAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 500, nullable = true)
    private String description;

    @Column(length = 100, nullable = false)
    private String reference;

    @Column(length = 50)
    private String correlationId;

    @Column(name = "failure_reason", length = 500, nullable = true)
    private String failureReason;
}
