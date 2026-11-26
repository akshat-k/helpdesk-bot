package com.akshat.ai.help_desk_bot.event.producer;

import com.akshat.ai.help_desk_bot.entity.OutboxEvent;
import com.akshat.ai.help_desk_bot.event.TicketEvent;
import com.akshat.ai.help_desk_bot.service.OutboxDeliveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class OutboxPublisherJob {

    private final OutboxDeliveryService deliveryService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, TicketEvent> kafkaTemplate;
    private final long sendTimeoutMillis;

    public OutboxPublisherJob(
            OutboxDeliveryService deliveryService,
            ObjectMapper objectMapper,
            KafkaTemplate<String, TicketEvent> kafkaTemplate,
            @Value("${app.outbox.send-timeout-ms:10000}") long sendTimeoutMillis
    ) {
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeoutMillis = sendTimeoutMillis;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publishDueEvents() {
        List<OutboxEvent> events = deliveryService.claimDueEvents();
        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            TicketEvent payload = objectMapper.readValue(event.getPayload(), TicketEvent.class);
            SendResult<String, TicketEvent> result = kafkaTemplate
                    .send(event.getTopic(), event.getAggregateId(), payload)
                    .get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
            deliveryService.markPublished(event.getId());
            log.info("Published ticket event id={} ticketId={} topic={} partition={} offset={}",
                    event.getId(),
                    event.getAggregateId(),
                    event.getTopic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception failure) {
            OutboxEvent.Status status = deliveryService.recordFailure(event.getId(), failure);
            log.error("Ticket event delivery failed id={} topic={} status={}",
                    event.getId(), event.getTopic(), status, failure);
        }
    }
}
