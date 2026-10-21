package com.walletpe.user.application.service;

import com.walletpe.user.domain.enums.OtpPurpose;
import com.walletpe.user.domain.models.OtpChallenge;
import com.walletpe.user.domain.models.User;
import com.walletpe.user.infrastructure.delivery.NotificationDeliveryService;
import com.walletpe.user.infrastructure.persistence.OtpRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OtpService {

    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationDeliveryService deliveryService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${otp.expiration-minutes:5}")
    private int expirationMinutes = 5;

    @Value("${otp.cooldown-seconds:60}")
    private int cooldownSeconds = 60;

    @Value("${otp.max-attempts:3}")
    private int maxAttempts = 3;

    public OtpService(OtpRepository otpRepository,
                      PasswordEncoder passwordEncoder,
                      NotificationDeliveryService deliveryService) {
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.deliveryService = deliveryService;
    }

    @Transactional
    public OtpChallenge createChallenge(User user, String targetIdentifier, OtpPurpose purpose, String channel) {
        // Enforce cooldown
        Optional<OtpChallenge> latestChallenge = otpRepository
                .findFirstByTargetIdentifierAndPurposeAndConsumedFalseOrderByCreatedAtDesc(targetIdentifier, purpose);

        if (latestChallenge.isPresent()) {
            LocalDateTime cooldownLimit = latestChallenge.get().getCreatedAt().plusSeconds(cooldownSeconds);
            if (LocalDateTime.now().isBefore(cooldownLimit)) {
                throw new IllegalStateException("Please wait before requesting a new OTP. Cooldown in effect.");
            }
        }

        // Invalidate previous outstanding active challenges for this target and purpose
        List<OtpChallenge> activeChallenges = otpRepository
                .findByTargetIdentifierAndPurposeAndConsumedFalse(targetIdentifier, purpose);
        for (OtpChallenge challenge : activeChallenges) {
            challenge.setConsumed(true);
            challenge.setConsumedAt(LocalDateTime.now());
        }
        otpRepository.saveAll(activeChallenges);

        // Generate 6-digit CSPRNG OTP
        String rawOtp = String.format("%06d", secureRandom.nextInt(1_000_000));
        String hashedOtp = passwordEncoder.encode(rawOtp);

        OtpChallenge newChallenge = new OtpChallenge();
        newChallenge.setUser(user);
        newChallenge.setTargetIdentifier(targetIdentifier);
        newChallenge.setPurpose(purpose);
        newChallenge.setChannel(channel);
        newChallenge.setOtpHash(hashedOtp);
        newChallenge.setMaxAttempts(maxAttempts);
        newChallenge.setAttemptCount(0);
        newChallenge.setVerified(false);
        newChallenge.setConsumed(false);
        newChallenge.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));

        OtpChallenge saved = otpRepository.save(newChallenge);

        // Deliver via abstraction
        if ("SMS".equalsIgnoreCase(channel)) {
            deliveryService.sendSms(targetIdentifier, "Your WalletPe verification code is: " + rawOtp);
        } else {
            deliveryService.sendEmail(targetIdentifier, "Your WalletPe Verification Code",
                    "Your OTP for " + purpose + " is: " + rawOtp + ". It expires in " + expirationMinutes + " minutes.");
        }

        return saved;
    }

    @Transactional
    public boolean verifyOtp(String targetIdentifier, OtpPurpose purpose, String rawOtp) {
        Optional<OtpChallenge> optionalChallenge = otpRepository
                .findFirstByTargetIdentifierAndPurposeAndConsumedFalseOrderByCreatedAtDesc(targetIdentifier, purpose);

        if (optionalChallenge.isEmpty()) {
            throw new IllegalArgumentException("No active verification challenge found.");
        }

        OtpChallenge challenge = optionalChallenge.get();

        if (challenge.isConsumed()) {
            throw new IllegalStateException("OTP has already been consumed.");
        }

        if (LocalDateTime.now().isAfter(challenge.getExpiresAt())) {
            challenge.setConsumed(true);
            challenge.setConsumedAt(LocalDateTime.now());
            otpRepository.save(challenge);
            throw new IllegalStateException("OTP has expired.");
        }

        if (challenge.getAttemptCount() >= challenge.getMaxAttempts()) {
            challenge.setConsumed(true);
            challenge.setConsumedAt(LocalDateTime.now());
            otpRepository.save(challenge);
            throw new IllegalStateException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        challenge.setAttemptCount(challenge.getAttemptCount() + 1);

        if (passwordEncoder.matches(rawOtp, challenge.getOtpHash())) {
            challenge.setVerified(true);
            challenge.setConsumed(true);
            challenge.setConsumedAt(LocalDateTime.now());
            otpRepository.save(challenge);
            return true;
        } else {
            if (challenge.getAttemptCount() >= challenge.getMaxAttempts()) {
                challenge.setConsumed(true);
                challenge.setConsumedAt(LocalDateTime.now());
            }
            otpRepository.save(challenge);
            return false;
        }
    }
}
