package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.dto.TicketRequest;
import com.akshat.ai.help_desk_bot.entity.Ticket;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HelpDeskService {

    // Create a new ticket
    Ticket createTicket(TicketRequest ticket);

    // Update an existing ticket identified by DB id
    Optional<Ticket> updateTicket(Long id, Ticket ticketUpdates);

    // Get a ticket by database id
    Optional<Ticket> getTicketById(Long id);

    // Get tickets by username (requester/owner)
    List<Ticket> getTicketByUsername(String username);

    List<Ticket> findRecentByUsernameAndTitle(String username, String title, LocalDateTime after);
}
