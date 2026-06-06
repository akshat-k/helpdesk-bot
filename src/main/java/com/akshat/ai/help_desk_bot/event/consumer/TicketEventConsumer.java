package com.akshat.ai.help_desk_bot.event.consumer;

import com.akshat.ai.help_desk_bot.event.TicketEvent;
import com.akshat.ai.help_desk_bot.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class TicketEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketEventConsumer.class);

    @Autowired
    private EmailService emailService;

    @KafkaListener(
            topics = "${app.kafka.topics.ticket-created}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTicketCreated(
            @Payload TicketEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Consumed CREATED event ticketId={} partition={} offset={}",
                event.getTicketId(), partition, offset);
        try {
            emailService.sendTicketCreatedEmail(event);
            ack.acknowledge(); // manual ack only after successful processing
        } catch (Exception e) {
            log.error("Failed processing CREATED event ticketId={}", event.getTicketId(), e);
            // Don't ack — Kafka will retry
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.ticket-updated}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTicketUpdated(
            @Payload TicketEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Consumed UPDATED event ticketId={} partition={} offset={}",
                event.getTicketId(), partition, offset);
        try {
            emailService.sendTicketUpdatedEmail(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed processing UPDATED event ticketId={}", event.getTicketId(), e);
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.ticket-resolved}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTicketResolved(
            @Payload TicketEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Consumed RESOLVED event ticketId={} partition={} offset={}",
                event.getTicketId(), partition, offset);
        try {
            emailService.sendTicketResolvedEmail(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed processing RESOLVED event ticketId={}", event.getTicketId(), e);
        }
    }
}