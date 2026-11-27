package com.akshat.ai.help_desk_bot;

import com.akshat.ai.help_desk_bot.entity.OutboxEvent;
import com.akshat.ai.help_desk_bot.event.TicketEvent;
import com.akshat.ai.help_desk_bot.event.producer.OutboxPublisherJob;
import com.akshat.ai.help_desk_bot.repository.DltReplayAuditRepository;
import com.akshat.ai.help_desk_bot.repository.OutboxEventRepository;
import com.akshat.ai.help_desk_bot.service.DltOperationsService;
import com.akshat.ai.help_desk_bot.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "app.security.allowed-domain=example.com",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.kafka.listener.auto-startup=false",
        "app.kafka.partitions=1",
        "app.kafka.replication-factor=1",
        "app.outbox.poll-interval-ms=3600000"
})
class OutboxKafkaIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.4")
            .withDatabaseName("helpdesk_it")
            .withUsername("helpdesk")
            .withPassword("integration-test-password");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private DltReplayAuditRepository dltReplayAuditRepository;

    @Autowired
    private DltOperationsService dltOperationsService;

    @Autowired
    private org.springframework.kafka.core.KafkaTemplate<String, TicketEvent> kafkaTemplate;

    @Autowired
    private OutboxPublisherJob publisherJob;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void clearOutbox() {
        outboxRepository.deleteAll();
        dltReplayAuditRepository.deleteAll();
    }

    @Test
    void migratesMysqlAndDeliversClaimedOutboxEventToKafka() throws Exception {
        String eventId = UUID.randomUUID().toString();
        outboxRepository.saveAndFlush(pendingEvent(eventId, eventPayload(eventId)));

        publisherJob.publishDueEvents();

        assertEquals(OutboxEvent.Status.PUBLISHED, outboxRepository.findById(eventId).orElseThrow().getStatus());
        assertKafkaContains(eventId);
    }

    @Test
    void retriesMalformedPayloadAndPublishesAfterOperatorCorrectsIt() throws Exception {
        String eventId = UUID.randomUUID().toString();
        outboxRepository.saveAndFlush(pendingEvent(eventId, "{"));

        publisherJob.publishDueEvents();

        OutboxEvent failed = outboxRepository.findById(eventId).orElseThrow();
        assertEquals(OutboxEvent.Status.PENDING, failed.getStatus());
        assertEquals(1, failed.getAttempts());
        failed.setPayload(eventPayload(eventId));
        failed.setAvailableAt(LocalDateTime.now().minusSeconds(1));
        outboxRepository.saveAndFlush(failed);

        publisherJob.publishDueEvents();

        assertEquals(OutboxEvent.Status.PUBLISHED, outboxRepository.findById(eventId).orElseThrow().getStatus());
    }

    @Test
    void skipsRowsLockedByAnotherConcurrentPublisher() throws Exception {
        String eventId = UUID.randomUUID().toString();
        outboxRepository.saveAndFlush(pendingEvent(eventId, eventPayload(eventId)));
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            var firstClaim = executor.submit(() -> transaction.execute(status -> {
                var rows = outboxRepository.findDueForUpdate(LocalDateTime.now(), 1);
                lockHeld.countDown();
                try {
                    if (!releaseLock.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release test row lock");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while holding test row lock", interrupted);
                }
                return rows;
            }));

            assertTrue(lockHeld.await(10, TimeUnit.SECONDS));
            var secondClaim = transaction.execute(status ->
                    outboxRepository.findDueForUpdate(LocalDateTime.now(), 1));
            assertTrue(secondClaim.isEmpty(), "SKIP LOCKED should keep concurrent publishers from claiming a locked row");
            releaseLock.countDown();
            assertEquals(1, firstClaim.get(10, TimeUnit.SECONDS).size());
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void reclaimsExpiredProcessingLeaseAfterPublisherRestart() throws Exception {
        String eventId = UUID.randomUUID().toString();
        OutboxEvent event = pendingEvent(eventId, eventPayload(eventId));
        event.setStatus(OutboxEvent.Status.PROCESSING);
        event.setLockUntil(LocalDateTime.now().minusSeconds(1));
        outboxRepository.saveAndFlush(event);

        publisherJob.publishDueEvents();

        assertEquals(OutboxEvent.Status.PUBLISHED, outboxRepository.findById(eventId).orElseThrow().getStatus());
    }

    @Test
    void inspectsAndAuditsDeadLetterReplay() throws Exception {
        String eventId = UUID.randomUUID().toString();
        TicketEvent payload = TicketEvent.builder()
                .eventId(eventId)
                .schemaVersion(1)
                .eventType(TicketEvent.EventType.UPDATED)
                .ticketId(456L)
                .title("Dead letter test")
                .occurredAt(LocalDateTime.now())
                .build();
        var metadata = kafkaTemplate.send("ticket-created.DLT", "456", payload)
                .get(10, TimeUnit.SECONDS)
                .getRecordMetadata();

        var records = dltOperationsService.listRecords("ticket-created", 10);
        assertTrue(records.stream().anyMatch(record -> record.eventId().equals(eventId)));

        assertEquals(DltOperationsService.ReplayResult.REPLAYED,
                dltOperationsService.replay("ticket-created", metadata.partition(), metadata.offset()));
        assertEquals(DltOperationsService.ReplayResult.ALREADY_REPLAYED,
                dltOperationsService.replay("ticket-created", metadata.partition(), metadata.offset()));
        assertKafkaContains(eventId);
    }

    private void assertKafkaContains(String eventId) {
        Properties consumerProperties = new Properties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-it-" + eventId);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(List.of("ticket-created"));
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            boolean found = false;
            while (!found && System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records.records("ticket-created")) {
                    if (record.value().contains(eventId)) {
                        found = true;
                        break;
                    }
                }
            }
            assertTrue(found, "Kafka should contain the event with its idempotency key");
        }
    }

    private OutboxEvent pendingEvent(String eventId, String payload) {
        return OutboxEvent.builder()
                .id(eventId)
                .topic("ticket-created")
                .aggregateId("123")
                .payload(payload)
                .status(OutboxEvent.Status.PENDING)
                .attempts(0)
                .availableAt(LocalDateTime.now().minusSeconds(1))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String eventPayload(String eventId) throws Exception {
        return objectMapper.writeValueAsString(TicketEvent.builder()
                .eventId(eventId)
                .schemaVersion(1)
                .eventType(TicketEvent.EventType.CREATED)
                .ticketId(123L)
                .title("Integration test ticket")
                .occurredAt(LocalDateTime.now())
                .build());
    }
}
