package com.akshat.ai.help_desk_bot.dto;

import com.akshat.ai.help_desk_bot.entity.Ticket;
import com.akshat.ai.help_desk_bot.enums.Priority;
import com.akshat.ai.help_desk_bot.enums.Status;

import java.time.LocalDateTime;

public record TicketResponse(
        Long id,
        String username,
        String title,
        String description,
        Status status,
        Priority priority,
        String assignee,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getUsername(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getAssignee(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
