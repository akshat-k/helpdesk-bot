package com.akshat.ai.help_desk_bot.cron;

import com.akshat.ai.help_desk_bot.memory.DatabaseChatMemory;
import com.akshat.ai.help_desk_bot.repository.ChatMessageRepository;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Component

public class ChatMemoryCleanUpJob {

    @Autowired
    private ChatMessageRepository chatMessageRepository;
    // Runs every day at midnight
    @Scheduled(cron = "0 0 0 * * *")
    public void deleteOldMessages() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        chatMessageRepository.deleteByCreatedAtBefore(cutoff);
    }
}
