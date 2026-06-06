package com.akshat.ai.help_desk_bot.event.producer;

import com.akshat.ai.help_desk_bot.event.TicketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class TicketEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TicketEventProducer.class);

    @Autowired
    private KafkaTemplate<String, TicketEvent> kafkaTemplate;

    @Value("${app.kafka.topics.ticket-created}")
    private String ticketCreatedTopic;

    @Value("${app.kafka.topics.ticket-updated}")
    private String ticketUpdatedTopic;

    @Value("${app.kafka.topics.ticket-resolved}")
    private String ticketResolvedTopic;

    public void publishTicketCreated(TicketEvent event) {
        publish(ticketCreatedTopic, event);
    }

    public void publishTicketUpdated(TicketEvent event) {
        publish(ticketUpdatedTopic, event);
    }

    public void publishTicketResolved(TicketEvent event) {
        publish(ticketResolvedTopic, event);
    }

    private void publish(String topic, TicketEvent event) {
        // Use ticketId as partition key — ensures all events for same ticket
        // go to the same partition, maintaining order
        String key = String.valueOf(event.getTicketId());

        CompletableFuture<SendResult<String, TicketEvent>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event={} ticketId={} topic={}",
                        event.getEventType(), event.getTicketId(), topic, ex);
            } else {
                log.info("Published event={} ticketId={} topic={} partition={} offset={}",
                        event.getEventType(),
                        event.getTicketId(),
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                );
            }
        });
    }
}
