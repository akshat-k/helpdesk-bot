package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.entity.Conversation;
import com.akshat.ai.help_desk_bot.repository.ChatMessageRepository;
import com.akshat.ai.help_desk_bot.repository.ConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationService {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    // Call this on every request to keep metadata fresh
    public void upsertConversation(String conversationId, String username) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElse(Conversation.builder()
                        .id(conversationId)
                        .username(username)
                        .status("ACTIVE")
                        .build());

        // Update username if we just learned it
        if (username != null && conversation.getUsername() == null) {
            conversation.setUsername(username);
        }

        conversationRepository.save(conversation);
    }

    public List<Conversation> getConversationsByUsername(String username) {
        return conversationRepository.findByUsernameOrderByLastActiveAtDesc(username);
    }

    public String getUsernameByConversationId(String conversationId) {
        return conversationRepository.findById(conversationId)
                .map(Conversation::getUsername)
                .orElse(null);
    }
}
