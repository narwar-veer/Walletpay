package com.walletpe.kyc.domain.utils;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

public final class AadhaarCryptoUtils {

    private AadhaarCryptoUtils() {}

    public static String hashAadhaar(String rawAadhaar, String hmacKey) {
        if (rawAadhaar == null || rawAadhaar.isBlank()) {
            throw new IllegalArgumentException("Aadhaar number cannot be blank");
        }
        String clean = rawAadhaar.replaceAll("\\s+", "");
        if (hmacKey == null || hmacKey.trim().length() < 32) {
            throw new IllegalStateException("KYC Aadhaar HMAC key must contain at least 32 characters");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(clean.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", e);
        }
    }

    public static String maskAadhaar(String rawAadhaar) {
        if (rawAadhaar == null || rawAadhaar.isBlank()) {
            return "XXXXXXXXXXXX";
        }
        String clean = rawAadhaar.replaceAll("\\s+", "");
        if (clean.length() < 4) {
            return "XXXXXXXXXXXX";
        }
        return "XXXXXXXX" + clean.substring(clean.length() - 4);
    }
}
