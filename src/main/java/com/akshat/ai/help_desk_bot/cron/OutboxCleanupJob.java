package com.akshat.ai.help_desk_bot.cron;

import com.akshat.ai.help_desk_bot.service.OutboxOperationsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxCleanupJob {

    private final OutboxOperationsService operationsService;

    public OutboxCleanupJob(OutboxOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron:0 30 2 * * *}")
    public void cleanupExpiredRecords() {
        operationsService.cleanupExpiredRecords();
    }
}
