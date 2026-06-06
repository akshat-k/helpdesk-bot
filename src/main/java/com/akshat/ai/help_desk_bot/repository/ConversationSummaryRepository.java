package com.akshat.ai.help_desk_bot.repository;

import com.akshat.ai.help_desk_bot.entity.ConversationSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationSummaryRepository extends JpaRepository<ConversationSummary, Long> {
    Optional<ConversationSummary> findByConversationId(String conversationId);
    void deleteByConversationId(String conversationId);
}
