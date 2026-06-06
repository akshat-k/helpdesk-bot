package com.akshat.ai.help_desk_bot.repository;

import com.akshat.ai.help_desk_bot.entity.Ticket;
import com.akshat.ai.help_desk_bot.enums.Priority;
import com.akshat.ai.help_desk_bot.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByStatus(Status status);
    List<Ticket> findByPriority(Priority priority);
    List<Ticket> findByUsername(String username);

    @Query("SELECT t FROM Ticket t WHERE t.username = :username " +
            "AND t.title = :title " +
            "AND t.createdAt >= :after")
    List<Ticket> findRecentByUsernameAndTitle(
            @Param("username") String username,
            @Param("title") String title,
            @Param("after") LocalDateTime after
    );
}
