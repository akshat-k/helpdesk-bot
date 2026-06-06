package com.akshat.ai.help_desk_bot.config;


import com.akshat.ai.help_desk_bot.event.TicketEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.ticket-created}")
    private String ticketCreatedTopic;

    @Value("${app.kafka.topics.ticket-updated}")
    private String ticketUpdatedTopic;

    @Value("${app.kafka.topics.ticket-resolved}")
    private String ticketResolvedTopic;

    // Auto-create topics with 3 partitions and replication factor 1 (use 3 in prod)
    @Bean
    public NewTopic ticketCreatedTopic() {
        return TopicBuilder.name(ticketCreatedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ticketUpdatedTopic() {
        return TopicBuilder.name(ticketUpdatedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ticketResolvedTopic() {
        return TopicBuilder.name(ticketResolvedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TicketEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, TicketEvent> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, TicketEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setConcurrency(3); // one thread per partition
        return factory;
    }
}
