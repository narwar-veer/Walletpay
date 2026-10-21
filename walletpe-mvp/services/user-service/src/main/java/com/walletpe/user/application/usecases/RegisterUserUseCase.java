package com.walletpe.user.application.usecases;

import com.walletpe.user.api.dto.SignupRequest;
import com.walletpe.user.api.dto.UserResponse;
import com.walletpe.user.application.mapper.UserMapper;
import com.walletpe.user.application.service.OtpService;
import com.walletpe.user.domain.enums.OtpPurpose;
import com.walletpe.user.domain.enums.UserRole;
import com.walletpe.user.domain.enums.UserStatus;
import com.walletpe.user.domain.models.User;
import com.walletpe.user.infrastructure.persistence.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;

@Service
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;

    public RegisterUserUseCase(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
    }

    @Transactional
    public UserResponse register(SignupRequest request) {
        // Business Rule 1: Age strictly older than 16 on registration date
        LocalDate today = LocalDate.now();
        int age = Period.between(request.getDateOfBirth(), today).getYears();
        if (age < 17) {
            throw new IllegalArgumentException("User must be strictly older than 16 years on the registration date.");
        }

        // Business Rule 4: Normalize email address consistently
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String mobileNumber = request.getMobileNumber().trim();

        // Business Rule 5: Prevent duplicate email and mobile
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email address already exists.");
        }
        if (userRepository.existsByMobileNumber(mobileNumber)) {
            throw new IllegalArgumentException("An account with this mobile number already exists.");
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setEmail(normalizedEmail);
        user.setMobileNumber(mobileNumber);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(false);
        user.setMobileVerified(false);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.PENDING_EMAIL_VERIFICATION);

        User savedUser = userRepository.save(user);

        // Generate and dispatch email verification OTP
        otpService.createChallenge(savedUser, normalizedEmail, OtpPurpose.EMAIL_VERIFICATION, "EMAIL");

        return UserMapper.toResponse(savedUser);
    }
}
