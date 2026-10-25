package com.walletpe.user.api.dto;

import com.walletpe.user.domain.enums.UserStatus;

/** Minimal internal contract used by KYC; intentionally excludes PII. */
public record UserEligibilityResponse(boolean emailVerified, boolean mobileVerified, UserStatus status,
                                      boolean eligibleForKyc) {
}
