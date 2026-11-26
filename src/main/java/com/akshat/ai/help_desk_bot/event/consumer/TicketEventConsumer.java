package com.akshat.ai.help_desk_bot.event.consumer;

import com.akshat.ai.help_desk_bot.constants.IntegrationConstants;
import com.akshat.ai.help_desk_bot.entity.ProcessedTicketEvent;
import com.akshat.ai.help_desk_bot.event.TicketEvent;
import com.akshat.ai.help_desk_bot.repository.ProcessedTicketEventRepository;
import com.akshat.ai.help_desk_bot.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@Slf4j
public class TicketEventConsumer {

    private final EmailService emailService;
    private final ProcessedTicketEventRepository processedEvents;

    public TicketEventConsumer(
            EmailService emailService,
            ProcessedTicketEventRepository processedEvents
    ) {
        this.emailService = emailService;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.ticket-created}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTicketCreated(TicketEvent event) {
        process(event, emailService::sendTicketCreatedEmail);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.ticket-updated}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTicketUpdated(TicketEvent event) {
        process(event, emailService::sendTicketUpdatedEmail);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.ticket-resolved}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTicketResolved(TicketEvent event) {
        process(event, emailService::sendTicketResolvedEmail);
    }

    private void process(TicketEvent event, Consumer<TicketEvent> action) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
            throw new IllegalArgumentException(IntegrationConstants.INVALID_TICKET_EVENT);
        }
        if (processedEvents.existsById(event.getEventId())) {
            log.info("Skipping duplicate ticket event id={}", event.getEventId());
            return;
        }

        action.accept(event);
        processedEvents.save(ProcessedTicketEvent.builder()
                .eventId(event.getEventId())
                .build());
        log.info("Processed ticket event id={} ticketId={}", event.getEventId(), event.getTicketId());
    }
}
