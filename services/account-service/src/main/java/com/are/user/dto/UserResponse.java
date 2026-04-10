package com.are.user.dto;

import com.are.common.model.UserStatus;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        UserStatus status) {
}
