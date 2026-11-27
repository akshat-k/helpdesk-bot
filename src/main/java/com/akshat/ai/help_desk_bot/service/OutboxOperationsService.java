package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.entity.OutboxEvent;
import com.akshat.ai.help_desk_bot.repository.OutboxEventRepository;
import com.akshat.ai.help_desk_bot.repository.ProcessedTicketEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class OutboxOperationsService {

    private final OutboxEventRepository outboxRepository;
    private final ProcessedTicketEventRepository processedEvents;
    private final long retentionDays;

    public OutboxOperationsService(
            OutboxEventRepository outboxRepository,
            ProcessedTicketEventRepository processedEvents,
            @org.springframework.beans.factory.annotation.Value("${app.outbox.retention-days:90}") long retentionDays
    ) {
        this.outboxRepository = outboxRepository;
        this.processedEvents = processedEvents;
        this.retentionDays = retentionDays;
    }

    @Transactional(readOnly = true)
    public Page<OutboxEvent> findDeadEvents(Pageable pageable) {
        return outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.Status.DEAD, pageable);
    }

    @Transactional
    public boolean replayDeadEvent(String eventId) {
        Optional<OutboxEvent> event = outboxRepository.findByIdAndStatus(eventId, OutboxEvent.Status.DEAD);
        if (event.isEmpty()) {
            return false;
        }

        OutboxEvent deadEvent = event.get();
        deadEvent.setStatus(OutboxEvent.Status.PENDING);
        deadEvent.setAttempts(0);
        deadEvent.setAvailableAt(LocalDateTime.now());
        deadEvent.setLockUntil(null);
        deadEvent.setLastError(null);
        return true;
    }

    @Transactional
    public void cleanupExpiredRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        outboxRepository.deleteByStatusAndPublishedAtBefore(OutboxEvent.Status.PUBLISHED, cutoff);
        processedEvents.deleteByProcessedAtBefore(cutoff);
    }
}
