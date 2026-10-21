package com.walletpe.user.application.usecases;

import com.walletpe.user.api.dto.ApiResponse;
import com.walletpe.user.api.dto.AuthResponse;
import com.walletpe.user.api.dto.LoginRequest;
import com.walletpe.user.api.dto.ResetPasswordRequest;
import com.walletpe.user.application.service.OtpService;
import com.walletpe.user.domain.enums.OtpPurpose;
import com.walletpe.user.domain.enums.UserStatus;
import com.walletpe.user.domain.models.User;
import com.walletpe.user.infrastructure.persistence.RefreshTokenRepository;
import com.walletpe.user.infrastructure.persistence.UserRepository;
import com.walletpe.user.infrastructure.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class LoginUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OtpService otpService;

    public LoginUseCase(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider,
                        OtpService otpService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.otpService = otpService;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getIdentifier().trim();
        Optional<User> userOptional;

        if (identifier.contains("@")) {
            userOptional = userRepository.findByEmail(identifier.toLowerCase());
        } else {
            userOptional = userRepository.findByMobileNumber(identifier);
            if (userOptional.isPresent() && !userOptional.get().isMobileVerified()) {
                throw new IllegalStateException("Mobile number login is not enabled until mobile number is verified.");
            }
        }

        if (userOptional.isEmpty()) {
            throw new IllegalArgumentException("Invalid credentials.");
        }

        User user = userOptional.get();

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials.");
        }

        if (user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION || !user.isEmailVerified()) {
            throw new IllegalStateException("Account email is not verified. Please verify your email before logging in.");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new IllegalStateException("Account is suspended.");
        }

        if (user.getStatus() == UserStatus.BLOCKED || user.getStatus() == UserStatus.CLOSED) {
            throw new IllegalStateException("Account is not active.");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtTokenProvider.createToken(user.getUserId(), user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }

    @Transactional
    public ApiResponse<String> forgotPassword(String email) {
        // Generic response to prevent account enumeration
        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.isEmailVerified() && user.getStatus() == UserStatus.ACTIVE) {
                try {
                    otpService.createChallenge(user, normalizedEmail, OtpPurpose.PASSWORD_RESET, "EMAIL");
                } catch (Exception ignored) {
                    // Ignore cooldown or errors silently to preserve enumeration safety
                }
            }
        }

        return ApiResponse.success("If an account exists with the provided email, a password reset code has been sent.", null);
    }

    @Transactional
    public ApiResponse<String> resetPassword(ResetPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid password reset request."));

        boolean verified = otpService.verifyOtp(normalizedEmail, OtpPurpose.PASSWORD_RESET, request.getOtp());
        if (!verified) {
            throw new IllegalArgumentException("Invalid or expired OTP.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke all refresh tokens for this user upon password reset
        refreshTokenRepository.findByUserAndRevokedFalse(user).forEach(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });

        return ApiResponse.success("Password reset successfully. You can now login with your new password.", null);
    }
}
