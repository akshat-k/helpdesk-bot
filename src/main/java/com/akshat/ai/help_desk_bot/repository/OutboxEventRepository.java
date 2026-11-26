package com.akshat.ai.help_desk_bot.repository;

import com.akshat.ai.help_desk_bot.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE (status = 'PENDING' AND available_at <= :now)
               OR (status = 'PROCESSING' AND lock_until <= :now)
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findDueForUpdate(
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize
    );

    Optional<OutboxEvent> findByIdAndStatus(String id, OutboxEvent.Status status);
}
