package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.entity.Conversation;
import com.akshat.ai.help_desk_bot.repository.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private ConversationService service;

    @Test
    void rejectsUsingAnotherUsersConversationId() {
        Conversation conversation = Conversation.builder().id("conversation-1").ownerSubject("owner-subject").build();
        when(conversationRepository.findById("conversation-1")).thenReturn(Optional.of(conversation));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.upsertConversation("conversation-1", "attacker-subject", "attacker"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void requiresConversationToBelongToAuthenticatedUser() {
        when(conversationRepository.findByIdAndOwnerSubject("conversation-1", "owner-subject"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.requireOwnedConversation("conversation-1", "owner-subject"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(conversationRepository).findByIdAndOwnerSubject("conversation-1", "owner-subject");
    }
}
