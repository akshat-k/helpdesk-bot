package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.entity.Conversation;
import com.akshat.ai.help_desk_bot.repository.ConversationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;

    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    // Call this on every request to keep metadata fresh
    public void upsertConversation(String conversationId, String ownerSubject, String username) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .map(existing -> {
                    if (!Objects.equals(existing.getOwnerSubject(), ownerSubject)) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
                    }
                    return existing;
                })
                .orElseGet(() -> Conversation.builder()
                        .id(conversationId)
                        .ownerSubject(ownerSubject)
                        .username(username)
                        .status("ACTIVE")
                        .build());

        conversationRepository.save(conversation);
    }

    public void requireOwnedConversation(String conversationId, String ownerSubject) {
        if (conversationId == null || conversationId.isBlank()
                || ownerSubject == null || ownerSubject.isBlank()
                || conversationRepository.findByIdAndOwnerSubject(conversationId, ownerSubject).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
    }

    public List<Conversation> getConversationsByOwner(String ownerSubject) {
        return conversationRepository.findByOwnerSubjectOrderByLastActiveAtDesc(ownerSubject);
    }

    public String getUsernameByConversationId(String conversationId) {
        return conversationRepository.findById(conversationId)
                .map(Conversation::getUsername)
                .orElse(null);
    }
}
