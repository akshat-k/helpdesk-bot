package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.entity.ChatMessage;
import com.akshat.ai.help_desk_bot.entity.ConversationSummary;
import com.akshat.ai.help_desk_bot.repository.ChatMessageRepository;
import com.akshat.ai.help_desk_bot.repository.ConversationSummaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConversationSummarizationService {

    private static final int SUMMARIZE_EVERY_N_MESSAGES = 10; // summarize after every 10 messages

    @Autowired
    private ConversationSummaryRepository summaryRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    @Lazy
    @Qualifier("SummarizationChatClient")
    private ChatClient chatClient;

    public void summarizeIfNeeded(String conversationId) {
        long messageCount = chatMessageRepository.countByConversationId(conversationId);

        if (messageCount % SUMMARIZE_EVERY_N_MESSAGES != 0) return;

        log.debug("Triggering summarization for conversationId={} at messageCount={}", conversationId, messageCount);

        // Fetch last N messages to summarize
        List<ChatMessage> recent = chatMessageRepository
                .findTop20ByConversationIdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(recent);

        String transcript = recent.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        // Get existing summary if any
        String existingSummary = summaryRepository.findByConversationId(conversationId)
                .map(ConversationSummary::getSummary)
                .orElse("");

        String prompt = String.format("""
                You are summarizing an IT support conversation for memory compression.
                
                %s
                
                Recent conversation:
                %s
                
                Write a concise summary (under 200 words) capturing:
                - The user's username if mentioned
                - Issues reported
                - Tickets created or updated (with IDs)
                - Current status of each issue
                - Any pending actions
                
                Return only the summary, no preamble.
                """,
                existingSummary.isBlank() ? "" : "Previous summary:\n" + existingSummary,
                transcript
        );

        try {
            String newSummary = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            ConversationSummary entity = summaryRepository
                    .findByConversationId(conversationId)
                    .orElse(ConversationSummary.builder()
                            .conversationId(conversationId)
                            .build());

            entity.setSummary(newSummary);
            entity.setMessageCount((int) messageCount);
            summaryRepository.save(entity);

            log.debug("Summary updated for conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("Summarization failed for conversationId={}", conversationId, e);
        }
    }

    public Optional<String> getSummary(String conversationId) {
        return summaryRepository.findByConversationId(conversationId)
                .map(ConversationSummary::getSummary);
    }
}
