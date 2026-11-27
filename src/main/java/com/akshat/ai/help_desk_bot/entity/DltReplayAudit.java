package com.akshat.ai.help_desk_bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dlt_replay_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DltReplayAudit {

    public enum Status {
        REPLAYING, REPLAYED
    }

    @Id
    @Column(length = 255)
    private String id;

    @Column(name = "source_topic", nullable = false, length = 200)
    private String sourceTopic;

    @Column(name = "partition_number", nullable = false)
    private int partition;

    @Column(name = "record_offset", nullable = false)
    private long recordOffset;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "replayed_at")
    private LocalDateTime replayedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
