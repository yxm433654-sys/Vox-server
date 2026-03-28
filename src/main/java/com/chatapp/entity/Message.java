package com.chatapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "message")
public class Message {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "sender_id", nullable = false)
    private Long senderId;
    
    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "resource_id")
    private Long resourceId;
    
    @Column(name = "video_resource_id")
    private Long videoResourceId;
    
    @Column(length = 16)
    private String status = "SENT";
    
    @Column(name = "create_time")
    private LocalDateTime createTime;
    
    @Column(name = "read_time")
    private LocalDateTime readTime;
    
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
    
    public enum MessageType {
        TEXT,
        IMAGE,
        VIDEO,
        DYNAMIC_PHOTO
    }
}
