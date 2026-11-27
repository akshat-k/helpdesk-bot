package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.entity.OutboxEvent;
import com.akshat.ai.help_desk_bot.repository.OutboxEventRepository;
import com.akshat.ai.help_desk_bot.repository.ProcessedTicketEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxOperationsServiceTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private ProcessedTicketEventRepository processedEvents;

    @Test
    void requeuesDeadEventAndResetsRetryState() {
        OutboxEvent event = OutboxEvent.builder()
                .id("event-1")
                .status(OutboxEvent.Status.DEAD)
                .attempts(8)
                .lastError("broker unavailable")
                .lockUntil(java.time.LocalDateTime.now())
                .build();
        when(outboxRepository.findByIdAndStatus("event-1", OutboxEvent.Status.DEAD))
                .thenReturn(Optional.of(event));

        OutboxOperationsService service = service();

        assertTrue(service.replayDeadEvent("event-1"));
        assertEquals(OutboxEvent.Status.PENDING, event.getStatus());
        assertEquals(0, event.getAttempts());
        assertNull(event.getLastError());
        assertNull(event.getLockUntil());
        assertNotNull(event.getAvailableAt());
    }

    @Test
    void refusesToReplayEventsThatAreNotDead() {
        when(outboxRepository.findByIdAndStatus("event-1", OutboxEvent.Status.DEAD))
                .thenReturn(Optional.empty());

        assertFalse(service().replayDeadEvent("event-1"));
    }

    @Test
    void retentionCleanupRemovesOnlyExpiredPublishedAndProcessedRows() {
        OutboxOperationsService service = service();

        service.cleanupExpiredRecords();

        verify(outboxRepository).deleteByStatusAndPublishedAtBefore(
                eq(OutboxEvent.Status.PUBLISHED), any(java.time.LocalDateTime.class));
        verify(processedEvents).deleteByProcessedAtBefore(any(java.time.LocalDateTime.class));
    }

    private OutboxOperationsService service() {
        return new OutboxOperationsService(outboxRepository, processedEvents, 90);
    }
}
