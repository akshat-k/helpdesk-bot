package com.akshat.ai.help_desk_bot.dto;

import com.akshat.ai.help_desk_bot.enums.Priority;
import com.akshat.ai.help_desk_bot.enums.Status;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateTicketApiRequest(
        @Schema(description = "Updated short summary")
        @Size(max = 200)
        String title,

        @Schema(description = "Updated issue description")
        @Size(max = 10000)
        String description,

        @Schema(description = "Updated priority")
        Priority priority,

        @Schema(description = "Updated lifecycle status")
        Status status,

        @Schema(description = "Updated assigned engineer")
        @Size(max = 255)
        String assignee
) {
    public boolean hasUpdates() {
        return title != null || description != null || priority != null || status != null || assignee != null;
    }
}
