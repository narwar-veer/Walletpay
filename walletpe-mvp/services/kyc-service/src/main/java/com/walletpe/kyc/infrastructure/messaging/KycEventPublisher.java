package com.walletpe.kyc.infrastructure.messaging;

import com.walletpe.kyc.domain.models.KycOutboxEvent;
import com.walletpe.kyc.infrastructure.persistence.KycOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Publishes committed outbox records; Kafka failures leave records pending for retry. */
@Component
@ConditionalOnProperty(name = "messaging.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class KycEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(KycEventPublisher.class);
    private final KycOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String approvedTopic;

    public KycEventPublisher(KycOutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                             @Value("${messaging.kafka.topic}") String approvedTopic) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.approvedTopic = approvedTopic;
    }

    @Scheduled(fixedDelayString = "${messaging.outbox.fixed-delay-ms:5000}")
    public void publishPending() {
        repository.findTop50ByStatusOrderByCreatedAtAsc("PENDING").forEach(this::publishOne);
    }

    @Transactional
    void publishOne(KycOutboxEvent event) {
        event.incrementAttemptCount();
        try {
            kafkaTemplate.send(approvedTopic, event.getPartitionKey(), event.getPayload()).get();
            event.markPublished(LocalDateTime.now());
            repository.save(event);
        } catch (Exception ex) {
            repository.save(event);
            log.warn("KYC outbox event {} remains pending after attempt {}", event.getEventId(), event.getAttemptCount());
        }
    }
}
