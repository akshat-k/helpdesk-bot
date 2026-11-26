package com.akshat.ai.help_desk_bot.config;


import com.akshat.ai.help_desk_bot.constants.IntegrationConstants;
import com.akshat.ai.help_desk_bot.event.TicketEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.ticket-created}")
    private String ticketCreatedTopic;

    @Value("${app.kafka.topics.ticket-updated}")
    private String ticketUpdatedTopic;

    @Value("${app.kafka.topics.ticket-resolved}")
    private String ticketResolvedTopic;

    @Value("${app.kafka.retry.max-attempts:3}")
    private long retryAttempts;

    @Value("${app.kafka.retry.backoff-ms:1000}")
    private long retryBackoffMillis;

    @Value("${app.kafka.partitions:3}")
    private int partitions;

    @Value("${app.kafka.replication-factor:1}")
    private int replicationFactor;

    private final KafkaTemplate<String, TicketEvent> kafkaTemplate;

    public KafkaConfig(KafkaTemplate<String, TicketEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Bean
    public NewTopic ticketCreatedTopic() {
        return TopicBuilder.name(ticketCreatedTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic ticketUpdatedTopic() {
        return TopicBuilder.name(ticketUpdatedTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic ticketResolvedTopic() {
        return TopicBuilder.name(ticketResolvedTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic ticketCreatedDeadLetterTopic() {
        return deadLetterTopic(ticketCreatedTopic);
    }

    @Bean
    public NewTopic ticketUpdatedDeadLetterTopic() {
        return deadLetterTopic(ticketUpdatedTopic);
    }

    @Bean
    public NewTopic ticketResolvedDeadLetterTopic() {
        return deadLetterTopic(ticketResolvedTopic);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TicketEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, TicketEvent> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, TicketEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(partitions);
        factory.setCommonErrorHandler(kafkaErrorHandler());
        return factory;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + IntegrationConstants.DEAD_LETTER_SUFFIX, record.partition()
                )
        );
        recoverer.setFailIfSendResultIsError(true);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(retryBackoffMillis, retryAttempts));
    }

    private NewTopic deadLetterTopic(String sourceTopic) {
        return TopicBuilder.name(sourceTopic + IntegrationConstants.DEAD_LETTER_SUFFIX)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }
}
