package com.akshat.ai.help_desk_bot.repository;

import com.akshat.ai.help_desk_bot.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, String> {
    List<Conversation> findByUsernameOrderByLastActiveAtDesc(String username);
    Optional<Conversation> findByIdAndUsername(String id, String username);
}
