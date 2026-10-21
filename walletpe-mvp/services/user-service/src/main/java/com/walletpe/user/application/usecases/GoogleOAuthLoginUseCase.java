package com.walletpe.user.application.usecases;

import com.walletpe.user.api.dto.ApiResponse;
import com.walletpe.user.api.dto.AuthResponse;
import org.springframework.stereotype.Service;

@Service
public class GoogleOAuthLoginUseCase {
    public ApiResponse<AuthResponse> loginWithGoogle(String idToken) {
        return ApiResponse.error("Google OAuth login will be activated in Phase 2.");
    }
}
