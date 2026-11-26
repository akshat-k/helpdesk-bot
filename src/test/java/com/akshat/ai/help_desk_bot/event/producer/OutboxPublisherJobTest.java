package com.akshat.ai.help_desk_bot.event.producer;

import com.akshat.ai.help_desk_bot.entity.OutboxEvent;
import com.akshat.ai.help_desk_bot.event.TicketEvent;
import com.akshat.ai.help_desk_bot.service.OutboxDeliveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherJobTest {

    @Mock
    private OutboxDeliveryService deliveryService;

    @Mock
    private KafkaTemplate<String, TicketEvent> kafkaTemplate;

    @Mock
    private SendResult<String, TicketEvent> sendResult;

    @Mock
    private RecordMetadata recordMetadata;

    @Test
    void publishesClaimedEventAndMarksItPublished() {
        OutboxEvent event = outboxEvent();
        when(deliveryService.claimDueEvents()).thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("ticket-created"), eq("42"), any(TicketEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);
        when(recordMetadata.partition()).thenReturn(1);
        when(recordMetadata.offset()).thenReturn(8L);

        publisher().publishDueEvents();

        verify(deliveryService).markPublished("event-1");
        verify(deliveryService, never()).recordFailure(eq("event-1"), any());
    }

    @Test
    void recordsKafkaFailureForRetry() {
        OutboxEvent event = outboxEvent();
        when(deliveryService.claimDueEvents()).thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("ticket-created"), eq("42"), any(TicketEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        publisher().publishDueEvents();

        verify(deliveryService, never()).markPublished("event-1");
        verify(deliveryService).recordFailure(eq("event-1"), any(Exception.class));
    }

    @Test
    void recordsMalformedOutboxPayloadAsDeliveryFailure() {
        OutboxEvent event = outboxEvent();
        event.setPayload("{");
        when(deliveryService.claimDueEvents()).thenReturn(List.of(event));

        publisher().publishDueEvents();

        verifyNoInteractions(kafkaTemplate);
        verify(deliveryService).recordFailure(eq("event-1"), any(Exception.class));
    }

    private OutboxPublisherJob publisher() {
        return new OutboxPublisherJob(deliveryService, new ObjectMapper(), kafkaTemplate, 1000);
    }

    private OutboxEvent outboxEvent() {
        return OutboxEvent.builder()
                .id("event-1")
                .topic("ticket-created")
                .aggregateId("42")
                .payload("""
                        {"eventId":"event-1","ticketId":42,"eventType":"CREATED","schemaVersion":1}
                        """)
                .status(OutboxEvent.Status.PROCESSING)
                .build();
    }
}
