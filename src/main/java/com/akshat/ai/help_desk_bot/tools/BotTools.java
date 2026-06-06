package com.akshat.ai.help_desk_bot.tools;

import com.akshat.ai.help_desk_bot.entity.Ticket;
import com.akshat.ai.help_desk_bot.enums.Status;
import com.akshat.ai.help_desk_bot.event.TicketEvent;
import com.akshat.ai.help_desk_bot.event.producer.TicketEventProducer;
import com.akshat.ai.help_desk_bot.service.HelpDeskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class BotTools {

    @Autowired
    private HelpDeskService helpDeskService;

    @Autowired
    private TicketEventProducer eventProducer;

    // Store email per conversationId — set from controller
    private final Map<String, String> conversationEmailMap = new ConcurrentHashMap<>();

    public void registerEmail(String conversationId, String email) {
        conversationEmailMap.put(conversationId, email);
    }

    @Tool(name = "create_ticket_tool", description = "This tool helps in creating a new ticket in database")
    public String createTicketTool(@ToolParam(description = "Ticket Details") Ticket ticket) {
        try {
            if (ticket == null) throw new IllegalArgumentException("Ticket payload is required");
            if (ticket.getId() != null && ticket.getId() <= 0) {
                ticket.setId(null);
            } else if (ticket.getId() != null) {
                // If caller provided an explicit id, ignore it to avoid accidental updates
                ticket.setId(null);
            }
            // Check for duplicate — same username + title created in last 30 seconds
            List<Ticket> recent = helpDeskService.findRecentByUsernameAndTitle(
                    ticket.getUsername(),
                    ticket.getTitle(),
                    LocalDateTime.now().minusSeconds(30)
            );

            if (!recent.isEmpty()) {
                Ticket existing = recent.get(0);
                log.warn("Duplicate ticket creation blocked for username={} title={}",
                        ticket.getUsername(), ticket.getTitle());
                return "Ticket already exists: \n" + existing.toString();
            }

            Ticket saved = helpDeskService.createTicket(ticket);

            // Publish Kafka event
            TicketEvent event = TicketEvent.builder()
                    .eventType(TicketEvent.EventType.CREATED)
                    .ticketId(saved.getId())
                    .username(saved.getUsername())
                    .userEmail(conversationEmailMap.getOrDefault(saved.getUsername(), ""))
                    .title(saved.getTitle())
                    .description(saved.getDescription())
                    .priority(saved.getPriority())
                    .status(saved.getStatus())
                    .assignee(saved.getAssignee())
                    .occurredAt(LocalDateTime.now())
                    .build();

            eventProducer.publishTicketCreated(event);

            return saved.toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to create ticket: " + ex.getMessage(), ex);
        }
    }

    @Tool(name = "update_ticket_tool", description = "This tool helps in updating an existing ticket in database")
    public String updateTicketTool(@ToolParam(description = "Ticket id") Long id,
                                   @ToolParam(description = "Ticket Details") Ticket ticket) {
        if (id == null || id <= 0) {
            return "Invalid ticket id for update: " + id;
        }
        try {
            if (ticket == null) ticket = new Ticket();
            ticket.setId(id);

            Optional<Ticket> updated = helpDeskService.updateTicket(id, ticket);

            if (updated.isEmpty()) return "Ticket #" + id + " not found.";

            Ticket saved = updated.get();

            boolean isResolved = saved.getStatus() == Status.RESOLVED
                    || saved.getStatus() == Status.CLOSED;

            TicketEvent event = TicketEvent.builder()
                    .eventType(isResolved
                            ? TicketEvent.EventType.RESOLVED
                            : TicketEvent.EventType.UPDATED)
                    .ticketId(saved.getId())
                    .username(saved.getUsername())
                    .userEmail(conversationEmailMap.getOrDefault(saved.getUsername(), ""))
                    .title(saved.getTitle())
                    .description(saved.getDescription())
                    .priority(saved.getPriority())
                    .status(saved.getStatus())
                    .assignee(saved.getAssignee() != null ? saved.getAssignee() : "Unassigned")
                    .occurredAt(LocalDateTime.now())
                    .build();

            if (isResolved) {
                eventProducer.publishTicketResolved(event);
            } else {
                eventProducer.publishTicketUpdated(event);
            }

            return saved.toString();

        } catch (Exception e) {
            log.error("Failed to update ticket id={}", id, e);
            return "Failed to update ticket id=" + id + ": " + e.getMessage();
        }
    }






    @Tool(name = "get_ticket_by_id_tool", description = "Retrieve a ticket by its database id")
    public String getTicketByIdTool(@ToolParam(description = "Database ticket id") Long id) {
        if (id == null || id <= 0) return "Invalid ticket id provided.";
        return helpDeskService.getTicketById(id)
                .map(ticket -> {
                    try {
                        return new ObjectMapper().writeValueAsString(ticket);
                    } catch (Exception e) {
                        return ticket.toString();
                    }
                })
                .orElse("No ticket found with id " + id);
    }

    @Tool(name = "get_tickets_by_username_tool", description = "Retrieve tickets by username")
    public String getTicketByUsernameTool(@ToolParam(description = "Username") String username) {
        if (username == null || username.isBlank()) return "Username is required.";
        List<Ticket> tickets = helpDeskService.getTicketByUsername(username);
        if (tickets.isEmpty()) return "No tickets found for username: " + username;
        try {
            return new ObjectMapper().writeValueAsString(tickets);
        } catch (Exception e) {
            return tickets.toString();
        }
    }

}
