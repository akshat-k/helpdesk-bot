package com.akshat.ai.help_desk_bot.event.consumer;

import com.akshat.ai.help_desk_bot.entity.ProcessedTicketEvent;
import com.akshat.ai.help_desk_bot.event.TicketEvent;
import com.akshat.ai.help_desk_bot.repository.ProcessedTicketEventRepository;
import com.akshat.ai.help_desk_bot.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketEventConsumerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private ProcessedTicketEventRepository processedEvents;

    @Test
    void sendsAndMarksCreatedEventProcessed() {
        TicketEventConsumer consumer = new TicketEventConsumer(emailService, processedEvents);
        TicketEvent event = event();

        consumer.onTicketCreated(event);

        verify(emailService).sendTicketCreatedEmail(event);
        verify(processedEvents).save(argThat(processed -> event.getEventId().equals(processed.getEventId())));
    }

    @Test
    void skipsAlreadyProcessedEvents() {
        TicketEventConsumer consumer = new TicketEventConsumer(emailService, processedEvents);
        TicketEvent event = event();
        when(processedEvents.existsById(event.getEventId())).thenReturn(true);

        consumer.onTicketUpdated(event);

        verifyNoInteractions(emailService);
        verify(processedEvents).existsById(event.getEventId());
        verify(processedEvents, never()).save(any(ProcessedTicketEvent.class));
    }

    @Test
    void doesNotMarkFailedEmailAsProcessedSoKafkaCanRetry() {
        TicketEventConsumer consumer = new TicketEventConsumer(emailService, processedEvents);
        TicketEvent event = event();
        doThrow(new IllegalStateException("mail unavailable"))
                .when(emailService).sendTicketResolvedEmail(event);

        assertThrows(IllegalStateException.class, () -> consumer.onTicketResolved(event));

        verify(processedEvents, never()).save(any(ProcessedTicketEvent.class));
    }

    @Test
    void rejectsEventsWithoutIdempotencyKey() {
        TicketEventConsumer consumer = new TicketEventConsumer(emailService, processedEvents);

        assertThrows(IllegalArgumentException.class, () -> consumer.onTicketCreated(new TicketEvent()));
        verifyNoInteractions(emailService, processedEvents);
    }

    private TicketEvent event() {
        return TicketEvent.builder()
                .eventId("event-1")
                .ticketId(42L)
                .eventType(TicketEvent.EventType.CREATED)
                .build();
    }
}
