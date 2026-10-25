package com.walletpe.kyc.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Defense in depth: KYC only accepts identity headers injected by the gateway. */
@Component
public class GatewayRequestVerifier {
    private final byte[] expectedSecret;
    private final boolean enforcementEnabled;

    public GatewayRequestVerifier(@Value("${kyc.gateway-shared-secret}") String secret,
                                  @Value("${kyc.enforce-gateway-header:true}") boolean enforcementEnabled) {
        if (secret == null || secret.length() < 32) throw new IllegalStateException("Gateway shared secret is not configured");
        this.expectedSecret = secret.getBytes(StandardCharsets.UTF_8);
        this.enforcementEnabled = enforcementEnabled;
    }

    public void verify(String suppliedSecret) {
        byte[] supplied = suppliedSecret == null ? new byte[0] : suppliedSecret.getBytes(StandardCharsets.UTF_8);
        if (enforcementEnabled && !MessageDigest.isEqual(expectedSecret, supplied)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Request must be made through the API gateway");
        }
    }
}
