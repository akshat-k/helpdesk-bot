package com.akshat.ai.help_desk_bot.repository;

import com.akshat.ai.help_desk_bot.entity.ProcessedTicketEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ProcessedTicketEventRepository extends JpaRepository<ProcessedTicketEvent, String> {

    long deleteByProcessedAtBefore(LocalDateTime cutoff);
}
