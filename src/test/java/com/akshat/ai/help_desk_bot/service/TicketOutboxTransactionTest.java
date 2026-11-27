package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.dto.TicketRequest;
import com.akshat.ai.help_desk_bot.repository.OutboxEventRepository;
import com.akshat.ai.help_desk_bot.repository.TicketRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:outbox-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.kafka.topics.ticket-created=ticket-created",
        "app.kafka.topics.ticket-updated=ticket-updated",
        "app.kafka.topics.ticket-resolved=ticket-resolved"
})
@TestPropertySource(properties = "spring.flyway.enabled=false")
@Import({HelpDeskServiceImpl.class, TicketEventOutboxService.class, TicketOutboxTransactionTest.TestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TicketOutboxTransactionTest {

    @Autowired
    private HelpDeskService helpDeskService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private FaultingObjectMapper objectMapper;

    @BeforeEach
    void clearDatabase() {
        outboxEventRepository.deleteAll();
        ticketRepository.deleteAll();
        objectMapper.setFailSerialization(false);
    }

    @Test
    void savesTicketAndOutboxEventInSameTransaction() {
        helpDeskService.createTicket(
                new TicketRequest("VPN failure", "Cannot connect", null, null, null),
                "google-sub",
                "employee",
                "employee@example.com"
        );

        assertEquals(1, ticketRepository.count());
        assertEquals(1, outboxEventRepository.count());
    }

    @Test
    void rollsBackTicketWhenOutboxSerializationFails() {
        objectMapper.setFailSerialization(true);

        assertThrows(IllegalStateException.class, () -> helpDeskService.createTicket(
                new TicketRequest("VPN failure", "Cannot connect", null, null, null),
                "google-sub",
                "employee",
                "employee@example.com"
        ));

        assertEquals(0, ticketRepository.count());
        assertEquals(0, outboxEventRepository.count());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FaultingObjectMapper testObjectMapper() {
            return new FaultingObjectMapper();
        }
    }

    static class FaultingObjectMapper extends ObjectMapper {
        private boolean failSerialization;

        FaultingObjectMapper() {
            registerModule(new JavaTimeModule());
        }

        void setFailSerialization(boolean failSerialization) {
            this.failSerialization = failSerialization;
        }

        @Override
        public String writeValueAsString(Object value) throws JsonProcessingException {
            if (failSerialization) {
                throw new JsonProcessingException("serialization failure") {
                };
            }
            return super.writeValueAsString(value);
        }
    }
}
