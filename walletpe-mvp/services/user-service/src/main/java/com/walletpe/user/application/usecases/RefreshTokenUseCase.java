package com.walletpe.user.application.usecases;

import com.walletpe.user.api.dto.ApiResponse;
import com.walletpe.user.api.dto.AuthResponse;
import com.walletpe.user.domain.models.RefreshToken;
import com.walletpe.user.domain.models.User;
import com.walletpe.user.infrastructure.persistence.RefreshTokenRepository;
import com.walletpe.user.infrastructure.security.JwtTokenProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class RefreshTokenUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public RefreshTokenUseCase(RefreshTokenRepository refreshTokenRepository, JwtTokenProvider jwtTokenProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public ApiResponse<AuthResponse> refreshToken(String tokenHash) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token."));

        if (LocalDateTime.now().isAfter(refreshToken.getExpiresAt())) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new IllegalStateException("Refresh token has expired.");
        }

        User user = refreshToken.getUser();
        String newToken = jwtTokenProvider.createToken(user.getUserId(), user.getEmail(), user.getRole().name());

        return ApiResponse.success("Token refreshed", AuthResponse.builder()
                .token(newToken)
                .tokenType("Bearer")
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .build());
    }
}
