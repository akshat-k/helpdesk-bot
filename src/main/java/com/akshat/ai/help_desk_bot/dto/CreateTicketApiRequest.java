package com.akshat.ai.help_desk_bot.dto;

import com.akshat.ai.help_desk_bot.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateTicketApiRequest(
        @Schema(description = "Short summary of the issue", example = "VPN authentication fails")
        @NotBlank
        @Size(max = 200)
        String title,

        @Schema(description = "Actionable description of the issue", example = "VPN login fails with error 809 since this morning.")
        @NotBlank
        @Size(max = 10000)
        String description,

        @Schema(description = "Ticket priority; defaults to MEDIUM when omitted")
        Priority priority
) {
}
