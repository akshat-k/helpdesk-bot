package com.akshat.ai.help_desk_bot.tools;

import com.akshat.ai.help_desk_bot.entity.Ticket;
import com.akshat.ai.help_desk_bot.service.HelpDeskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class BotTools {

    @Autowired
    private HelpDeskService helpDeskService;

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

            return helpDeskService.createTicket(ticket).toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to create ticket: " + ex.getMessage(), ex);
        }
    }

    @Tool(name = "update_ticket_tool", description = "This tool helps in updating a existing ticket in database")
    public String updateTicketTool(@ToolParam(description = "Ticket id") Long id,
                                   @ToolParam(description = "Ticket Details") Ticket ticket) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid ticket id for update: " + id);
        }
        try {
            if (ticket == null) ticket = new Ticket();
            // Force the id on the provided ticket to avoid mismatches and accidental creation
            ticket.setId(id);
            return helpDeskService.updateTicket(id, ticket).toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to update ticket id=" + id + ": " + ex.getMessage(), ex);
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
