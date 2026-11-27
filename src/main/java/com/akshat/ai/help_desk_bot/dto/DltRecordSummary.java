package com.akshat.ai.help_desk_bot.dto;

import com.akshat.ai.help_desk_bot.event.TicketEvent;

public record DltRecordSummary(
        String topic,
        int partition,
        long offset,
        long timestamp,
        String eventId,
        Long ticketId,
        TicketEvent.EventType eventType
) {
}
