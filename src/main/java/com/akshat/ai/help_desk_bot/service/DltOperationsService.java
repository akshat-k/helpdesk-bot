package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.constants.IntegrationConstants;
import com.akshat.ai.help_desk_bot.dto.DltRecordSummary;
import com.akshat.ai.help_desk_bot.entity.DltReplayAudit;
import com.akshat.ai.help_desk_bot.event.TicketEvent;
import com.akshat.ai.help_desk_bot.repository.DltReplayAuditRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class DltOperationsService {

    public enum ReplayResult {
        REPLAYED, ALREADY_REPLAYED, NOT_FOUND
    }

    private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(250);
    private static final long REPLAY_TIMEOUT_SECONDS = 10;

    private final ConsumerFactory<String, TicketEvent> consumerFactory;
    private final KafkaTemplate<String, TicketEvent> kafkaTemplate;
    private final DltReplayAuditRepository auditRepository;
    private final List<String> sourceTopics;

    public DltOperationsService(
            ConsumerFactory<String, TicketEvent> consumerFactory,
            KafkaTemplate<String, TicketEvent> kafkaTemplate,
            DltReplayAuditRepository auditRepository,
            @Value("${app.kafka.topics.ticket-created}") String createdTopic,
            @Value("${app.kafka.topics.ticket-updated}") String updatedTopic,
            @Value("${app.kafka.topics.ticket-resolved}") String resolvedTopic
    ) {
        this.consumerFactory = consumerFactory;
        this.kafkaTemplate = kafkaTemplate;
        this.auditRepository = auditRepository;
        this.sourceTopics = List.of(createdTopic, updatedTopic, resolvedTopic);
    }

    public List<DltRecordSummary> listRecords(String sourceTopic, int requestedLimit) {
        String dltTopic = dltTopic(sourceTopic);
        int limit = Math.max(1, Math.min(requestedLimit, IntegrationConstants.MAX_DLT_PAGE_SIZE));
        try (Consumer<String, TicketEvent> consumer = newConsumer()) {
            List<TopicPartition> partitions = partitions(consumer, dltTopic);
            if (partitions.isEmpty()) {
                return List.of();
            }
            Map<TopicPartition, Long> starts = consumer.beginningOffsets(partitions, METADATA_TIMEOUT);
            Map<TopicPartition, Long> ends = consumer.endOffsets(partitions, METADATA_TIMEOUT);
            consumer.assign(partitions);
            for (TopicPartition partition : partitions) {
                long end = ends.getOrDefault(partition, 0L);
                long start = starts.getOrDefault(partition, 0L);
                consumer.seek(partition, Math.max(start, end - limit));
            }

            List<DltRecordSummary> summaries = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < deadline
                    && summaries.size() < limit * partitions.size()
                    && !caughtUp(consumer, partitions, ends)) {
                for (ConsumerRecord<String, TicketEvent> record : consumer.poll(POLL_TIMEOUT)) {
                    TicketEvent event = requireTicketEvent(record);
                    summaries.add(summary(record, event));
                    if (summaries.size() >= limit * partitions.size()) {
                        break;
                    }
                }
            }
            return summaries.stream()
                    .sorted(Comparator.comparingLong(DltRecordSummary::timestamp).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    @Transactional
    public ReplayResult replay(String sourceTopic, int partition, long offset) {
        String dltTopic = dltTopic(sourceTopic);
        if (partition < 0 || offset < 0) {
            throw new IllegalArgumentException("Partition and offset must be non-negative");
        }
        Optional<ConsumerRecord<String, TicketEvent>> record = findRecord(dltTopic, partition, offset);
        if (record.isEmpty()) {
            return ReplayResult.NOT_FOUND;
        }

        ConsumerRecord<String, TicketEvent> dltRecord = record.get();
        TicketEvent event = requireTicketEvent(dltRecord);
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            throw new IllegalArgumentException(IntegrationConstants.INVALID_TICKET_EVENT);
        }

        String auditId = sourceTopic + ":" + partition + ":" + offset;
        if (auditRepository.existsById(auditId)) {
            return ReplayResult.ALREADY_REPLAYED;
        }
        DltReplayAudit audit = auditRepository.saveAndFlush(DltReplayAudit.builder()
                .id(auditId)
                .sourceTopic(sourceTopic)
                .partition(partition)
                .recordOffset(offset)
                .eventId(event.getEventId())
                .status(DltReplayAudit.Status.REPLAYING)
                .build());

        try {
            kafkaTemplate.send(sourceTopic, dltRecord.key(), event)
                    .get(REPLAY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not replay dead-letter record to its source topic", failure);
        }

        audit.setStatus(DltReplayAudit.Status.REPLAYED);
        audit.setReplayedAt(LocalDateTime.now());
        return ReplayResult.REPLAYED;
    }

    private String dltTopic(String sourceTopic) {
        if (!sourceTopics.contains(sourceTopic)) {
            throw new IllegalArgumentException(IntegrationConstants.UNKNOWN_DLT_SOURCE_TOPIC);
        }
        return sourceTopic + IntegrationConstants.DEAD_LETTER_SUFFIX;
    }

    private Consumer<String, TicketEvent> newConsumer() {
        String identity = UUID.randomUUID().toString();
        return consumerFactory.createConsumer("dlt-ops-" + identity, "dlt-ops", identity);
    }

    private List<TopicPartition> partitions(Consumer<String, TicketEvent> consumer, String topic) {
        return Optional.ofNullable(consumer.partitionsFor(topic, METADATA_TIMEOUT))
                .orElseGet(List::of)
                .stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .toList();
    }

    private Optional<ConsumerRecord<String, TicketEvent>> findRecord(String topic, int partition, long offset) {
        try (Consumer<String, TicketEvent> consumer = newConsumer()) {
            TopicPartition target = new TopicPartition(topic, partition);
            List<TopicPartition> available = partitions(consumer, topic);
            if (!available.contains(target)) {
                return Optional.empty();
            }
            Map<TopicPartition, Long> starts = consumer.beginningOffsets(List.of(target), METADATA_TIMEOUT);
            Map<TopicPartition, Long> ends = consumer.endOffsets(List.of(target), METADATA_TIMEOUT);
            if (offset < starts.getOrDefault(target, 0L) || offset >= ends.getOrDefault(target, 0L)) {
                return Optional.empty();
            }
            consumer.assign(List.of(target));
            consumer.seek(target, offset);
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, TicketEvent> record : consumer.poll(POLL_TIMEOUT)) {
                    if (record.offset() == offset) {
                        return Optional.of(record);
                    }
                    if (record.offset() > offset) {
                        return Optional.empty();
                    }
                }
            }
            return Optional.empty();
        }
    }

    private boolean caughtUp(
            Consumer<String, TicketEvent> consumer,
            List<TopicPartition> partitions,
            Map<TopicPartition, Long> ends
    ) {
        return partitions.stream().allMatch(partition ->
                consumer.position(partition, METADATA_TIMEOUT) >= ends.getOrDefault(partition, 0L));
    }

    private TicketEvent requireTicketEvent(ConsumerRecord<String, TicketEvent> record) {
        if (record.value() == null) {
            throw new IllegalStateException("Dead-letter record has no deserializable ticket event");
        }
        return record.value();
    }

    private DltRecordSummary summary(ConsumerRecord<String, TicketEvent> record, TicketEvent event) {
        return new DltRecordSummary(
                record.topic(),
                record.partition(),
                record.offset(),
                record.timestamp(),
                event.getEventId(),
                event.getTicketId(),
                event.getEventType()
        );
    }
}
