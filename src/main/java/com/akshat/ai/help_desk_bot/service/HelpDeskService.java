package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.dto.TicketRequest;
import com.akshat.ai.help_desk_bot.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HelpDeskService {

    // Create a new ticket
    Ticket createTicket(TicketRequest ticket, String ownerSubject, String ownerUsername, String userEmail);

    // Update an existing ticket only when it belongs to the authenticated owner
    Optional<Ticket> updateTicket(Long id, String ownerSubject, Ticket ticketUpdates, String userEmail);

    // Get a ticket by database id only when it belongs to the authenticated owner
    Optional<Ticket> getTicketById(Long id, String ownerSubject);

    Page<Ticket> getTicketsForOwner(String ownerSubject, Pageable pageable);

    List<Ticket> findRecentByOwnerAndTitle(String ownerSubject, String title, LocalDateTime after);
}
