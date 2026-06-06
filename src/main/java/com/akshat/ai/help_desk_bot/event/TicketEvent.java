package com.akshat.ai.help_desk_bot.event;

import com.akshat.ai.help_desk_bot.enums.Priority;
import com.akshat.ai.help_desk_bot.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketEvent implements Serializable {

    public enum EventType {
        CREATED, UPDATED, RESOLVED, CLOSED
    }

    private EventType eventType;
    private Long ticketId;
    private String username;
    private String userEmail;
    private String title;
    private String description;
    private Priority priority;
    private Status status;
    private String assignee;
    private LocalDateTime occurredAt;
}
