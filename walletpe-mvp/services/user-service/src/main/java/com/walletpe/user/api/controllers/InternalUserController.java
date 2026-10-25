package com.walletpe.user.api.controllers;

import com.walletpe.user.api.dto.UserEligibilityResponse;
import com.walletpe.user.domain.enums.UserStatus;
import com.walletpe.user.domain.models.User;
import com.walletpe.user.infrastructure.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Private service-to-service API. Deployment must additionally restrict this path to the private network.
 */
@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserController {
    private final UserRepository userRepository;
    private final byte[] internalServiceSecret;

    public InternalUserController(UserRepository userRepository,
                                  @Value("${internal-service.shared-secret}") String internalServiceSecret) {
        if (internalServiceSecret == null || internalServiceSecret.length() < 32) {
            throw new IllegalStateException("internal-service.shared-secret must be configured with at least 32 characters");
        }
        this.userRepository = userRepository;
        this.internalServiceSecret = internalServiceSecret.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/{userId}/eligibility")
    public UserEligibilityResponse eligibility(@PathVariable UUID userId,
            @RequestHeader("X-Internal-Service-Key") String suppliedSecret) {
        if (!MessageDigest.isEqual(internalServiceSecret, suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized internal caller");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        boolean eligible = user.isEmailVerified() && user.isMobileVerified() && user.getStatus() == UserStatus.ACTIVE;
        return new UserEligibilityResponse(user.isEmailVerified(), user.isMobileVerified(), user.getStatus(), eligible);
    }
}
