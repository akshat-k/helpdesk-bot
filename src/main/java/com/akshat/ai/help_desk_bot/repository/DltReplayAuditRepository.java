package com.akshat.ai.help_desk_bot.repository;

import com.akshat.ai.help_desk_bot.entity.DltReplayAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DltReplayAuditRepository extends JpaRepository<DltReplayAudit, String> {
}
