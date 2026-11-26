package com.akshat.ai.help_desk_bot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "conversations", indexes = {
        @Index(columnList = "username"),
        @Index(columnList = "owner_subject")
})
public class Conversation {

    @Id
    private String id; // conversationId UUID

    @Column(name = "username")
    private String username;

    @Column(name = "owner_subject")
    private String ownerSubject;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt;

    @Column(name = "status")
    private String status = "ACTIVE"; // ACTIVE, CLOSED

    @PrePersist
    protected void onCreate() {
        this.startedAt = LocalDateTime.now();
        this.lastActiveAt = this.startedAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastActiveAt = LocalDateTime.now();
    }
}
