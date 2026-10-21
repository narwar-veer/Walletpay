package com.walletpe.user.application.mapper;

import com.walletpe.user.api.dto.UserResponse;
import com.walletpe.user.domain.models.User;

public class UserMapper {
    public static UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .dateOfBirth(user.getDateOfBirth())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .emailVerified(user.isEmailVerified())
                .mobileVerified(user.isMobileVerified())
                .role(user.getRole())
                .status(user.getStatus())
                .pendingEmail(user.getPendingEmail())
                .pendingMobileNumber(user.getPendingMobileNumber())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
