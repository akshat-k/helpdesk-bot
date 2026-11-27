package com.akshat.ai.help_desk_bot.config;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean("BotChatClient")
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory){
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    //Second bean for summarization Service to have correct conversationId
    @Bean("SummarizationChatClient")
    public ChatClient summarizationChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor()) // ← no memory advisor
                .build();
    }
}
