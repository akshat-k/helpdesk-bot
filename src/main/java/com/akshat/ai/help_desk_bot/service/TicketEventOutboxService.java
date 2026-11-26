package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.constants.IntegrationConstants;
import com.akshat.ai.help_desk_bot.entity.OutboxEvent;
import com.akshat.ai.help_desk_bot.entity.Ticket;
import com.akshat.ai.help_desk_bot.event.TicketEvent;
import com.akshat.ai.help_desk_bot.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TicketEventOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String ticketCreatedTopic;
    private final String ticketUpdatedTopic;
    private final String ticketResolvedTopic;

    public TicketEventOutboxService(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.ticket-created}") String ticketCreatedTopic,
            @Value("${app.kafka.topics.ticket-updated}") String ticketUpdatedTopic,
            @Value("${app.kafka.topics.ticket-resolved}") String ticketResolvedTopic
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.ticketCreatedTopic = ticketCreatedTopic;
        this.ticketUpdatedTopic = ticketUpdatedTopic;
        this.ticketResolvedTopic = ticketResolvedTopic;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Ticket ticket, TicketEvent.EventType eventType, String userEmail) {
        String eventId = UUID.randomUUID().toString();
        TicketEvent event = TicketEvent.builder()
                .eventId(eventId)
                .schemaVersion(IntegrationConstants.EVENT_SCHEMA_VERSION)
                .eventType(eventType)
                .ticketId(ticket.getId())
                .username(ticket.getUsername())
                .userEmail(userEmail)
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .assignee(ticket.getAssignee())
                .occurredAt(LocalDateTime.now())
                .build();

        try {
            outboxEventRepository.save(OutboxEvent.builder()
                    .id(eventId)
                    .topic(topicFor(eventType))
                    .aggregateId(String.valueOf(ticket.getId()))
                    .payload(objectMapper.writeValueAsString(event))
                    .status(OutboxEvent.Status.PENDING)
                    .attempts(0)
                    .availableAt(LocalDateTime.now())
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize ticket event for the outbox", e);
        }
    }

    private String topicFor(TicketEvent.EventType eventType) {
        return switch (eventType) {
            case CREATED -> ticketCreatedTopic;
            case UPDATED -> ticketUpdatedTopic;
            case RESOLVED, CLOSED -> ticketResolvedTopic;
        };
    }
}
