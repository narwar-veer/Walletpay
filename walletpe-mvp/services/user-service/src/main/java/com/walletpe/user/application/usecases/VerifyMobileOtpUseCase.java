package com.walletpe.user.application.usecases;

import com.walletpe.user.api.dto.ApiResponse;
import com.walletpe.user.application.service.OtpService;
import com.walletpe.user.domain.enums.OtpPurpose;
import com.walletpe.user.domain.models.User;
import com.walletpe.user.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class VerifyMobileOtpUseCase {

    private final UserRepository userRepository;
    private final OtpService otpService;

    public VerifyMobileOtpUseCase(UserRepository userRepository, OtpService otpService) {
        this.userRepository = userRepository;
        this.otpService = otpService;
    }

    @Transactional
    public ApiResponse<String> initiateMobileVerification(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isMobileVerified()) {
            throw new IllegalStateException("Mobile number is already verified.");
        }

        otpService.createChallenge(user, user.getMobileNumber(), OtpPurpose.MOBILE_VERIFICATION, "SMS");

        return ApiResponse.success("Mobile verification OTP sent successfully.", null);
    }

    @Transactional
    public ApiResponse<String> verifyMobile(UUID userId, String otp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean verified = otpService.verifyOtp(user.getMobileNumber(), OtpPurpose.MOBILE_VERIFICATION, otp);

        if (!verified) {
            throw new IllegalArgumentException("Invalid OTP provided.");
        }

        user.setMobileVerified(true);
        user.setMobileVerifiedAt(LocalDateTime.now());
        userRepository.save(user);

        return ApiResponse.success("Mobile number verified successfully. Eligible to proceed with KYC.", null);
    }
}
