package com.akshat.ai.help_desk_bot.repository;

import com.akshat.ai.help_desk_bot.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    void deleteByConversationId(String conversationId);

    void deleteByCreatedAtBefore(LocalDateTime cutoff);

    List<ChatMessage> findTop20ByConversationIdOrderByCreatedAtDesc(String conversationId);

    long countByConversationId(String conversationId);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversationId = :conversationId ORDER BY m.createdAt DESC LIMIT :limit")
    List<ChatMessage> findTopNByConversationIdOrderByCreatedAtDesc(
            @Param("conversationId") String conversationId,
            @Param("limit") int limit
    );

}
