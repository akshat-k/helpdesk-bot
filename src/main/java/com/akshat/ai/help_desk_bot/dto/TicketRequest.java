package com.akshat.ai.help_desk_bot.dto;

import lombok.Builder;

@Builder
public record TicketRequest(
        String username,
        String title,
        String description,
        String status,
        String priority,
        String assignee
) {}

