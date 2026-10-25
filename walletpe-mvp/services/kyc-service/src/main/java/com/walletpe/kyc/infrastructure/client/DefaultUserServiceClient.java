package com.walletpe.kyc.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.UUID;

@Component
public class DefaultUserServiceClient implements UserServiceClient {

    private final RestClient restClient;
    private final String internalServiceSecret;

    public DefaultUserServiceClient(@Value("${services.user-service.url}") String userServiceUrl,
                                     @Value("${internal-service.shared-secret}") String internalServiceSecret) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(3_000);
        this.restClient = RestClient.builder().baseUrl(userServiceUrl).requestFactory(requestFactory).build();
        this.internalServiceSecret = internalServiceSecret;
    }

    @Override
    public UserEligibilityResponse checkEligibility(UUID userId) {
        try {
            return restClient.get()
                    .uri("/internal/v1/users/{userId}/eligibility", userId)
                    .header("X-Internal-Service-Key", internalServiceSecret)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(UserEligibilityResponse.class);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "User eligibility service is temporarily unavailable");
        }
    }
}
