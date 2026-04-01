package com.vox.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "conversation")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user1_id", nullable = false)
    private Long user1Id;

    @Column(name = "user2_id", nullable = false)
    private Long user2Id;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_time")
    private LocalDateTime lastMessageTime;

    @Column(name = "unread_count_user1", nullable = false)
    private Integer unreadCountUser1 = 0;

    @Column(name = "unread_count_user2", nullable = false)
    private Integer unreadCountUser2 = 0;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        final LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        updateTime = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    public boolean involves(Long userId) {
        return userId != null && (userId.equals(user1Id) || userId.equals(user2Id));
    }

    public Long peerIdOf(Long userId) {
        if (!involves(userId)) {
            throw new IllegalArgumentException("User is not in this session");
        }
        return userId.equals(user1Id) ? user2Id : user1Id;
    }

    public int unreadCountFor(Long userId) {
        if (userId == null) {
            return 0;
        }
        if (userId.equals(user1Id)) {
            return unreadCountUser1 == null ? 0 : unreadCountUser1;
        }
        if (userId.equals(user2Id)) {
            return unreadCountUser2 == null ? 0 : unreadCountUser2;
        }
        return 0;
    }

    public void incrementUnreadFor(Long userId) {
        if (userId == null) {
            return;
        }
        if (userId.equals(user1Id)) {
            unreadCountUser1 = unreadCountFor(user1Id) + 1;
            return;
        }
        if (userId.equals(user2Id)) {
            unreadCountUser2 = unreadCountFor(user2Id) + 1;
        }
    }

    public void clearUnreadFor(Long userId) {
        if (userId == null) {
            return;
        }
        if (userId.equals(user1Id)) {
            unreadCountUser1 = 0;
            return;
        }
        if (userId.equals(user2Id)) {
            unreadCountUser2 = 0;
        }
    }
}

