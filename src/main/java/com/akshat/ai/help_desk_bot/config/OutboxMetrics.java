package com.akshat.ai.help_desk_bot.config;

import com.akshat.ai.help_desk_bot.entity.OutboxEvent;
import com.akshat.ai.help_desk_bot.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.LocalDateTime;

@Configuration
public class OutboxMetrics {

    public OutboxMetrics(OutboxEventRepository repository, MeterRegistry registry) {
        for (OutboxEvent.Status status : OutboxEvent.Status.values()) {
            registry.gauge(
                    "helpdesk.outbox.events",
                    io.micrometer.core.instrument.Tags.of("status", status.name()),
                    repository,
                    outboxRepository -> outboxRepository.countByStatus(status)
            );
        }
        registry.gauge(
                "helpdesk.outbox.oldest.pending.age.seconds",
                repository,
                outboxRepository -> outboxRepository
                        .findOldestCreatedAtByStatus(OutboxEvent.Status.PENDING)
                        .map(createdAt -> Duration.between(createdAt, LocalDateTime.now()).toSeconds())
                        .orElse(0L)
        );
    }
}
