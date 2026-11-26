package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.constants.IntegrationConstants;
import com.akshat.ai.help_desk_bot.entity.OutboxEvent;
import com.akshat.ai.help_desk_bot.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxDeliveryService {

    private final OutboxEventRepository repository;
    private final int batchSize;
    private final int maxAttempts;
    private final long leaseSeconds;
    private final long initialBackoffMillis;
    private final long maxBackoffMillis;

    public OutboxDeliveryService(
            OutboxEventRepository repository,
            @Value("${app.outbox.batch-size:50}") int batchSize,
            @Value("${app.outbox.max-attempts:8}") int maxAttempts,
            @Value("${app.outbox.lease-seconds:60}") long leaseSeconds,
            @Value("${app.outbox.initial-backoff-ms:1000}") long initialBackoffMillis,
            @Value("${app.outbox.max-backoff-ms:60000}") long maxBackoffMillis
    ) {
        this.repository = repository;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.leaseSeconds = leaseSeconds;
        this.initialBackoffMillis = initialBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
    }

    @Transactional
    public List<OutboxEvent> claimDueEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> events = repository.findDueForUpdate(now, batchSize);
        for (OutboxEvent event : events) {
            event.setStatus(OutboxEvent.Status.PROCESSING);
            event.setLockUntil(now.plusSeconds(leaseSeconds));
        }
        return events;
    }

    @Transactional
    public void markPublished(String eventId) {
        OutboxEvent event = repository.findByIdAndStatus(eventId, OutboxEvent.Status.PROCESSING)
                .orElseThrow(() -> new IllegalStateException("Claimed outbox event is no longer available"));
        event.setStatus(OutboxEvent.Status.PUBLISHED);
        event.setPublishedAt(LocalDateTime.now());
        event.setLockUntil(null);
        event.setLastError(null);
    }

    @Transactional
    public OutboxEvent.Status recordFailure(String eventId, Exception failure) {
        OutboxEvent event = repository.findByIdAndStatus(eventId, OutboxEvent.Status.PROCESSING)
                .orElseThrow(() -> new IllegalStateException("Claimed outbox event is no longer available"));
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setLockUntil(null);
        event.setLastError(errorMessage(failure));

        if (attempts >= maxAttempts) {
            event.setStatus(OutboxEvent.Status.DEAD);
            return event.getStatus();
        }

        event.setStatus(OutboxEvent.Status.PENDING);
        event.setAvailableAt(LocalDateTime.now().plusNanos(backoffMillis(attempts) * 1_000_000));
        return event.getStatus();
    }

    private long backoffMillis(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 30);
        long delay;
        try {
            delay = Math.multiplyExact(initialBackoffMillis, multiplier);
        } catch (ArithmeticException e) {
            delay = maxBackoffMillis;
        }
        return Math.min(delay, maxBackoffMillis);
    }

    private String errorMessage(Exception failure) {
        String message = failure.getMessage() == null
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
        return message.length() <= IntegrationConstants.MAX_OUTBOX_ERROR_LENGTH
                ? message
                : message.substring(0, IntegrationConstants.MAX_OUTBOX_ERROR_LENGTH);
    }
}
