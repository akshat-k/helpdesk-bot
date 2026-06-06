package com.akshat.ai.help_desk_bot.memory;
import com.akshat.ai.help_desk_bot.entity.ChatMessage;
import com.akshat.ai.help_desk_bot.repository.ChatMessageRepository;
import com.akshat.ai.help_desk_bot.repository.ConversationSummaryRepository;
import com.akshat.ai.help_desk_bot.service.ConversationService;
import com.akshat.ai.help_desk_bot.service.ConversationSummarizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class DatabaseChatMemory implements ChatMemory {

    private static final int RECENT_MESSAGE_LIMIT = 6; // messages after last summary

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ConversationSummaryRepository summaryRepository;

    @Autowired
    private ConversationSummarizationService summarizationService;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ConversationService conversationService;

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (conversationId == null || messages == null || messages.isEmpty()) return;

        String detectedUsername = conversationService.getUsernameByConversationId(conversationId);

        for (Message message : messages) {
            try {
                String json = mapper.writeValueAsString(Map.of(
                        "type", message.getMessageType().name(),
                        "content", message.getText()
                ));
                ChatMessage chatMessage = ChatMessage.builder()
                        .conversationId(conversationId)
                        .role(message.getMessageType().getValue())
                        .username(detectedUsername)
                        .content(json)
                        .build();
                chatMessageRepository.save(chatMessage);
            } catch (Exception e) {
                log.error("Failed to persist message for conversationId={}", conversationId, e);
            }
        }
        summarizationService.summarizeIfNeeded(conversationId);
    }

    @Override
    public List<Message> get(String conversationId) {
        if (conversationId == null) return Collections.emptyList();

        List<Message> result = new ArrayList<>();

        // 1. Inject summary as a system message if available
        summaryRepository.findByConversationId(conversationId).ifPresent(s -> {
            result.add(new SystemMessage(
                    "Conversation summary so far:\n" + s.getSummary()
            ));
        });

        // 2. Append only recent messages after the summary
        List<ChatMessage> recent = chatMessageRepository
                .findTopNByConversationIdOrderByCreatedAtDesc(conversationId, RECENT_MESSAGE_LIMIT);
        Collections.reverse(recent);

        recent.stream()
                .map(row -> deserialize(row.getContent()))
                .filter(Objects::nonNull)
                .forEach(result::add);

        return result;
    }

    @Override
    public void clear(String conversationId) {
        chatMessageRepository.deleteByConversationId(conversationId);
        summaryRepository.deleteByConversationId(conversationId);
    }

    private Message deserialize(String json) {
        try {
            Map<String, String> map = mapper.readValue(json, Map.class);
            return switch (map.get("type")) {
                case "USER"      -> new UserMessage(map.get("content"));
                case "ASSISTANT" -> new AssistantMessage(map.get("content"));
                case "SYSTEM"    -> new SystemMessage(map.get("content"));
                default -> null;
            };
        } catch (Exception e) {
            log.error("Failed to deserialize message: {}", json, e);
            return null;
        }
    }
    private String extractUsername(List<Message> messages) {
        for (Message message : messages) {
            String text = message.getText();
            if (text == null) continue;

            // Matches patterns like "username: akshat123" or "my username is akshat123"
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?i)(?:username[:\\s]+|my username is\\s+)([a-zA-Z0-9_]+)")
                    .matcher(text);

            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }
}