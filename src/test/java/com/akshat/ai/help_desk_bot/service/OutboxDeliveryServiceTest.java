package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.entity.OutboxEvent;
import com.akshat.ai.help_desk_bot.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxDeliveryServiceTest {

    @Mock
    private OutboxEventRepository repository;

    private OutboxDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new OutboxDeliveryService(repository, 10, 3, 60, 100, 1000);
    }

    @Test
    void claimsDueEventsAndSetsLease() {
        OutboxEvent event = pendingEvent();
        when(repository.findDueForUpdate(any(), eq(10))).thenReturn(List.of(event));

        List<OutboxEvent> claimed = service.claimDueEvents();

        assertEquals(1, claimed.size());
        assertEquals(OutboxEvent.Status.PROCESSING, event.getStatus());
        assertTrue(event.getLockUntil().isAfter(LocalDateTime.now()));
    }

    @Test
    void marksSuccessfullyPublishedEvents() {
        OutboxEvent event = processingEvent();
        when(repository.findByIdAndStatus("event-1", OutboxEvent.Status.PROCESSING))
                .thenReturn(Optional.of(event));

        service.markPublished("event-1");

        assertEquals(OutboxEvent.Status.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertNull(event.getLockUntil());
        assertNull(event.getLastError());
    }

    @Test
    void retriesFailuresUsingBoundedExponentialBackoff() {
        OutboxEvent event = processingEvent();
        when(repository.findByIdAndStatus("event-1", OutboxEvent.Status.PROCESSING))
                .thenReturn(Optional.of(event));

        OutboxEvent.Status status = service.recordFailure("event-1", new RuntimeException("broker unavailable"));

        assertEquals(OutboxEvent.Status.PENDING, status);
        assertEquals(1, event.getAttempts());
        assertTrue(event.getAvailableAt().isAfter(LocalDateTime.now()));
        assertEquals("broker unavailable", event.getLastError());
        assertNull(event.getLockUntil());
    }

    @Test
    void movesEventsToDeadStateAfterMaximumAttempts() {
        OutboxEvent event = processingEvent();
        event.setAttempts(2);
        when(repository.findByIdAndStatus("event-1", OutboxEvent.Status.PROCESSING))
                .thenReturn(Optional.of(event));

        OutboxEvent.Status status = service.recordFailure("event-1", new RuntimeException("permanent failure"));

        assertEquals(OutboxEvent.Status.DEAD, status);
        assertEquals(3, event.getAttempts());
    }

    @Test
    void truncatesLongFailureDetails() {
        OutboxEvent event = processingEvent();
        when(repository.findByIdAndStatus("event-1", OutboxEvent.Status.PROCESSING))
                .thenReturn(Optional.of(event));

        service.recordFailure("event-1", new RuntimeException("x".repeat(1500)));

        assertEquals(1000, event.getLastError().length());
    }

    private OutboxEvent pendingEvent() {
        OutboxEvent event = processingEvent();
        event.setStatus(OutboxEvent.Status.PENDING);
        event.setAvailableAt(LocalDateTime.now().minusSeconds(1));
        event.setLockUntil(null);
        return event;
    }

    private OutboxEvent processingEvent() {
        return OutboxEvent.builder()
                .id("event-1")
                .topic("ticket-created")
                .aggregateId("42")
                .payload("{}")
                .status(OutboxEvent.Status.PROCESSING)
                .attempts(0)
                .availableAt(LocalDateTime.now())
                .lockUntil(LocalDateTime.now().plusSeconds(60))
                .build();
    }
}
