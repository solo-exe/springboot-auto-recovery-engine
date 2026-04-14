package com.are.common.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "otps")
public class OTPEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "otp", nullable = false)
    private String otp;

    @Column(name = "email", nullable = true)
    private String email;

    @Column(name = "phone_number", nullable = true)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient", nullable = false)
    private OTPRecipient recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OTPType type;

    @Column(name = "used_at", nullable = true)
    private LocalDateTime usedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
