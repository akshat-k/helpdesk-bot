package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.entity.OutboxEvent;
import com.akshat.ai.help_desk_bot.entity.Ticket;
import com.akshat.ai.help_desk_bot.event.TicketEvent;
import com.akshat.ai.help_desk_bot.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketEventOutboxServiceTest {

    @Mock
    private OutboxEventRepository repository;

    private TicketEventOutboxService service;

    @BeforeEach
    void setUp() {
        service = new TicketEventOutboxService(
                repository,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                "created-topic",
                "updated-topic",
                "resolved-topic"
        );
    }

    @Test
    void storesCreatedTicketEventInPendingOutbox() throws Exception {
        Ticket ticket = Ticket.builder()
                .id(42L)
                .username("employee")
                .title("VPN failure")
                .description("Cannot connect")
                .build();

        service.enqueue(ticket, TicketEvent.EventType.CREATED, "employee@example.com");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent row = captor.getValue();
        TicketEvent payload = new ObjectMapper().registerModule(new JavaTimeModule())
                .readValue(row.getPayload(), TicketEvent.class);

        assertEquals("created-topic", row.getTopic());
        assertEquals("42", row.getAggregateId());
        assertEquals(OutboxEvent.Status.PENDING, row.getStatus());
        assertEquals(row.getId(), payload.getEventId());
        assertEquals("employee@example.com", payload.getUserEmail());
        assertEquals(1, payload.getSchemaVersion());
        assertEquals(TicketEvent.EventType.CREATED, payload.getEventType());
    }

    @Test
    void routesUpdatedAndResolvedEventTypesToConfiguredTopics() {
        Ticket ticket = Ticket.builder().id(1L).title("Issue").build();

        service.enqueue(ticket, TicketEvent.EventType.UPDATED, "user@example.com");
        service.enqueue(ticket, TicketEvent.EventType.RESOLVED, "user@example.com");
        service.enqueue(ticket, TicketEvent.EventType.CLOSED, "user@example.com");

        verify(repository).save(argThat(event -> "updated-topic".equals(event.getTopic())));
        verify(repository, times(2)).save(argThat(event -> "resolved-topic".equals(event.getTopic())));
    }
}
