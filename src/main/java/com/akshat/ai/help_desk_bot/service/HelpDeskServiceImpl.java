package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.service.HelpDeskService;
import com.akshat.ai.help_desk_bot.entity.Ticket;
import com.akshat.ai.help_desk_bot.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class HelpDeskServiceImpl implements HelpDeskService {

    private static final Logger log = LoggerFactory.getLogger(HelpDeskServiceImpl.class);

    private final TicketRepository ticketRepository;

    @Autowired
    public HelpDeskServiceImpl(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Override
    public Ticket createTicket(Ticket ticket) {
        // Defensive: ignore any provided id
        if (ticket == null) throw new IllegalArgumentException("Ticket cannot be null");
        ticket.setId(null);

        // Ensure business rules: title must be present
        if (ticket.getTitle() == null || ticket.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Ticket title must not be empty");
        }
        // Save the ticket; Ticket entity PrePersist will set createdAt/updatedAt
        Ticket saved = ticketRepository.save(ticket);
        log.info("Created ticket id={} username={}", saved.getId(), saved.getUsername());
        return saved;
    }

    @Override
    @Transactional
    public Optional<Ticket> updateTicket(Long id, Ticket ticketUpdates) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        Optional<Ticket> existing = ticketRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Ticket t = existing.get();

        // if there's nothing to update, return early with the existing ticket
        if (ticketUpdates == null) {
            return Optional.of(t);
        }

        // Prevent accidental id changes
        ticketUpdates.setId(t.getId());

        // Pre-validate username change
        String newUsername = ticketUpdates.getUsername();
        if (newUsername != null) {
            newUsername = newUsername.trim();
            if (!newUsername.isEmpty() && !newUsername.equals(t.getUsername())) {
                log.warn("Changing username to {} for ticket id={}", newUsername, t.getId());
            }
        }

        AtomicBoolean changed = new AtomicBoolean(false);

        Optional.ofNullable(ticketUpdates.getTitle()).ifPresent(v -> {
            String trimmed = v == null ? null : v.trim();
            if (trimmed != null && !trimmed.equals(t.getTitle())) {
                t.setTitle(trimmed);
                changed.set(true);
            }
        });

        Optional.ofNullable(ticketUpdates.getDescription()).ifPresent(v -> {
            String trimmed = v == null ? null : v.trim();
            if (trimmed != null && !trimmed.equals(t.getDescription())) {
                t.setDescription(trimmed);
                changed.set(true);
            }
        });

        Optional.ofNullable(ticketUpdates.getStatus()).ifPresent(v -> {
            if (v != null && v != t.getStatus()) {
                t.setStatus(v);
                changed.set(true);
            }
        });

        Optional.ofNullable(ticketUpdates.getPriority()).ifPresent(v -> {
            if (v != null && v != t.getPriority()) {
                t.setPriority(v);
                changed.set(true);
            }
        });

        Optional.ofNullable(ticketUpdates.getAssignee()).ifPresent(v -> {
            String trimmed = v == null ? null : v.trim();
            if (trimmed != null && !trimmed.equals(t.getAssignee())) {
                t.setAssignee(trimmed);
                changed.set(true);
            }
        });

        Optional.ofNullable(newUsername).ifPresent(v -> {
            if (!v.equals(t.getUsername())) {
                t.setUsername(v);
                changed.set(true);
            }
        });

        if (!changed.get()) {
            log.debug("No changes detected for ticket id={}", t.getId());
            return Optional.of(t);
        }

        // Save and return updated ticket
        Ticket saved = ticketRepository.save(t);
        log.info("Updated ticket id={} changesApplied=true", saved.getId());
        return Optional.of(saved);
    }

    @Override
    public Optional<Ticket> getTicketById(Long id) {
        return ticketRepository.findById(id);
    }

    @Override
    public List<Ticket> getTicketByUsername(String username) {
        if (username == null) return List.of();
        return ticketRepository.findByUsername(username.trim());
    }

    @Override
    public List<Ticket> findRecentByUsernameAndTitle(String username, String title, LocalDateTime after) {

        return ticketRepository.findRecentByUsernameAndTitle(username,title,after);
    }
}
