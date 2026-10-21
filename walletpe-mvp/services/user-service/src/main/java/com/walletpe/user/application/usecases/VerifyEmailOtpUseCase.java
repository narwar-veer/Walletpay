package com.walletpe.user.application.usecases;

import com.walletpe.user.api.dto.ApiResponse;
import com.walletpe.user.application.service.OtpService;
import com.walletpe.user.domain.enums.OtpPurpose;
import com.walletpe.user.domain.enums.UserStatus;
import com.walletpe.user.domain.models.User;
import com.walletpe.user.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VerifyEmailOtpUseCase {

    private final UserRepository userRepository;
    private final OtpService otpService;

    public VerifyEmailOtpUseCase(UserRepository userRepository, OtpService otpService) {
        this.userRepository = userRepository;
        this.otpService = otpService;
    }

    @Transactional
    public ApiResponse<String> verifyEmail(String email, String otp) {
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + normalizedEmail));

        boolean verified = otpService.verifyOtp(normalizedEmail, OtpPurpose.EMAIL_VERIFICATION, otp);

        if (!verified) {
            throw new IllegalArgumentException("Invalid OTP provided.");
        }

        user.setEmailVerified(true);
        user.setEmailVerifiedAt(LocalDateTime.now());
        if (user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        return ApiResponse.success("Email verified successfully. Account is now active.", null);
    }

    @Transactional
    public ApiResponse<String> resendEmailOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + normalizedEmail));

        if (user.isEmailVerified()) {
            throw new IllegalStateException("Email is already verified.");
        }

        otpService.createChallenge(user, normalizedEmail, OtpPurpose.EMAIL_VERIFICATION, "EMAIL");

        return ApiResponse.success("Verification code resent successfully.", null);
    }
}
