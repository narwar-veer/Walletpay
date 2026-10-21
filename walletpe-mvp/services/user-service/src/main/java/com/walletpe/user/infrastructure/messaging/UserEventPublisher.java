package com.walletpe.user.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    public void publishUserRegistered(UUID userId, String email) {
        log.info("[Event] User registered event published: userId={}, email={}", userId, email);
    }

    public void publishUserActivated(UUID userId, String email) {
        log.info("[Event] User activated event published: userId={}, email={}", userId, email);
    }
}
