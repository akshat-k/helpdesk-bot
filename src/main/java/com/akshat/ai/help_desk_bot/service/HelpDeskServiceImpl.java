package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.dto.TicketRequest;
import com.akshat.ai.help_desk_bot.enums.Priority;
import com.akshat.ai.help_desk_bot.enums.Status;
import com.akshat.ai.help_desk_bot.service.HelpDeskService;
import com.akshat.ai.help_desk_bot.entity.Ticket;
import com.akshat.ai.help_desk_bot.event.TicketEvent;
import com.akshat.ai.help_desk_bot.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final TicketEventOutboxService ticketEventOutboxService;

    @Autowired
    public HelpDeskServiceImpl(
            TicketRepository ticketRepository,
            TicketEventOutboxService ticketEventOutboxService
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketEventOutboxService = ticketEventOutboxService;
    }

    @Override
    @Transactional
    public Ticket createTicket(TicketRequest request, String ownerSubject, String ownerUsername, String userEmail) {
        if (request == null) throw new IllegalArgumentException("Ticket request cannot be null");
        if (ownerSubject == null || ownerSubject.isBlank()
                || ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Authenticated ticket owner is required");
        }
        if (request.title() == null || request.title().trim().isEmpty()) {
            throw new IllegalArgumentException("Ticket title must not be empty");
        }

        Ticket ticket = Ticket.builder()
                .ownerSubject(ownerSubject)
                .username(ownerUsername)
                .title(request.title().trim())
                .description(request.description())
                .status(request.status() != null
                        ? Status.valueOf(request.status().toUpperCase())
                        : Status.OPEN)
                .priority(request.priority() != null
                        ? Priority.valueOf(request.priority().toUpperCase())
                        : Priority.MEDIUM)
                .assignee(request.assignee() != null ? request.assignee() : "Unassigned")
                .build();

        Ticket saved = ticketRepository.save(ticket);
        ticketEventOutboxService.enqueue(saved, TicketEvent.EventType.CREATED, userEmail);
        log.info("Created ticket id={} username={}", saved.getId(), saved.getUsername());
        return saved;
    }

    @Override
    @Transactional
    public Optional<Ticket> updateTicket(Long id, String ownerSubject, Ticket ticketUpdates, String userEmail) {
        if (id == null || id <= 0 || ownerSubject == null || ownerSubject.isBlank()) {
            return Optional.empty();
        }
        Optional<Ticket> existing = ticketRepository.findByIdAndOwnerSubject(id, ownerSubject);
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

        if (!changed.get()) {
            log.debug("No changes detected for ticket id={}", t.getId());
            return Optional.of(t);
        }

        // Save and return updated ticket
        Ticket saved = ticketRepository.save(t);
        TicketEvent.EventType eventType = saved.getStatus() == Status.RESOLVED || saved.getStatus() == Status.CLOSED
                ? TicketEvent.EventType.RESOLVED
                : TicketEvent.EventType.UPDATED;
        ticketEventOutboxService.enqueue(saved, eventType, userEmail);
        log.info("Updated ticket id={} changesApplied=true", saved.getId());
        return Optional.of(saved);
    }

    @Override
    public Optional<Ticket> getTicketById(Long id, String ownerSubject) {
        if (id == null || id <= 0 || ownerSubject == null || ownerSubject.isBlank()) {
            return Optional.empty();
        }
        return ticketRepository.findByIdAndOwnerSubject(id, ownerSubject);
    }

    @Override
    public Page<Ticket> getTicketsForOwner(String ownerSubject, Pageable pageable) {
        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new IllegalArgumentException("Authenticated ticket owner is required");
        }
        return ticketRepository.findAllByOwnerSubject(ownerSubject, pageable);
    }

    @Override
    public List<Ticket> findRecentByOwnerAndTitle(String ownerSubject, String title, LocalDateTime after) {
        if (ownerSubject == null || ownerSubject.isBlank()) return List.of();
        return ticketRepository.findRecentByOwnerSubjectAndTitle(ownerSubject, title, after);
    }
}
