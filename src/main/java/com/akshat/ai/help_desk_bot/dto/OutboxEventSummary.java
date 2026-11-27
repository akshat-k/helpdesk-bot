package com.akshat.ai.help_desk_bot.dto;

import com.akshat.ai.help_desk_bot.entity.OutboxEvent;

import java.time.LocalDateTime;

public record OutboxEventSummary(
        String id,
        String topic,
        String aggregateId,
        int attempts,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime availableAt
) {
    public static OutboxEventSummary from(OutboxEvent event) {
        return new OutboxEventSummary(
                event.getId(),
                event.getTopic(),
                event.getAggregateId(),
                event.getAttempts(),
                event.getLastError(),
                event.getCreatedAt(),
                event.getAvailableAt()
        );
    }
}
