package com.akshat.ai.help_desk_bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_ticket_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedTicketEvent {

    @Id
    @Column(length = 36)
    private String eventId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;

    @PrePersist
    void onCreate() {
        if (processedAt == null) processedAt = LocalDateTime.now();
    }
}
