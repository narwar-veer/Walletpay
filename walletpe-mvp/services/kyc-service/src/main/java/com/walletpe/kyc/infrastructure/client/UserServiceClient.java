package com.walletpe.kyc.infrastructure.client;

import java.util.UUID;

public interface UserServiceClient {

    record UserEligibilityResponse(
            boolean emailVerified,
            boolean mobileVerified,
            String status,
            boolean eligibleForKyc
    ) {}

    UserEligibilityResponse checkEligibility(UUID userId);
}
