package com.chatapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "file_resource")
public class FileResource {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "original_name")
    private String originalName;
    
    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private FileType fileType;
    
    @Column(name = "source_type", length = 32)
    private String sourceType;
    
    @Column(name = "mime_type", length = 64)
    private String mimeType;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    private Integer width;
    
    private Integer height;
    
    private Float duration;
    
    @Column(name = "cover_time")
    private Float coverTime;
    
    @Column(name = "related_file_id")
    private Long relatedFileId;
    
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
    
    @Column(name = "create_time")
    private LocalDateTime createTime;
    
    @Column(name = "uploader_id")
    private Long uploaderId;
    
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
    
    public enum FileType {
        IMAGE,
        VIDEO,
        DYNAMIC_COVER,
        DYNAMIC_VIDEO
    }
}
