DROP DATABASE IF EXISTS chatdb;
CREATE DATABASE chatdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE chatdb;

CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'User ID',
    username VARCHAR(64) NOT NULL COMMENT 'Username',
    password_hash VARCHAR(255) NOT NULL COMMENT 'Password hash',
    avatar_url VARCHAR(255) NULL COMMENT 'Avatar URL',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 1 active, 0 disabled',
    CONSTRAINT uk_user_username UNIQUE (username),
    INDEX idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User table';

CREATE TABLE file_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Attachment ID',
    original_name VARCHAR(255) NULL COMMENT 'Original file name',
    storage_path VARCHAR(512) NOT NULL COMMENT 'Object storage path',
    file_type ENUM('IMAGE', 'VIDEO', 'DYNAMIC_COVER', 'DYNAMIC_VIDEO') NOT NULL COMMENT 'Stored resource type',
    source_type VARCHAR(32) NULL COMMENT 'Source type such as Normal, iOS_LivePhoto, Android_MotionPhoto',
    mime_type VARCHAR(64) NULL COMMENT 'MIME type',
    file_size BIGINT NULL COMMENT 'Size in bytes',
    width INT NULL COMMENT 'Media width',
    height INT NULL COMMENT 'Media height',
    duration FLOAT NULL COMMENT 'Duration in seconds',
    cover_time FLOAT NULL COMMENT 'Cover frame time in seconds',
    related_file_id BIGINT NULL COMMENT 'Related resource ID',
    metadata_json TEXT NULL COMMENT 'Extended metadata JSON',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    uploader_id BIGINT NULL COMMENT 'Uploader user ID',
    INDEX idx_file_storage_path (storage_path(255)),
    INDEX idx_file_related (related_file_id),
    INDEX idx_file_uploader (uploader_id),
    INDEX idx_file_source_type (source_type),
    CONSTRAINT fk_file_uploader FOREIGN KEY (uploader_id) REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Attachment resource table';

CREATE TABLE message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Message ID',
    sender_id BIGINT NOT NULL COMMENT 'Sender user ID',
    receiver_id BIGINT NOT NULL COMMENT 'Receiver user ID',
    type ENUM('TEXT', 'IMAGE', 'VIDEO', 'DYNAMIC_PHOTO', 'FILE') NOT NULL COMMENT 'Message type',
    content TEXT NULL COMMENT 'Text content',
    resource_id BIGINT NULL COMMENT 'Primary resource ID such as image or cover',
    video_resource_id BIGINT NULL COMMENT 'Video resource ID',
    status VARCHAR(16) NOT NULL DEFAULT 'SENT' COMMENT 'SENT, DELIVERED, READ',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    read_time TIMESTAMP NULL COMMENT 'Read time',
    INDEX idx_message_receiver_id (receiver_id, id),
    INDEX idx_message_receiver_status (receiver_id, status, id),
    INDEX idx_message_sender_receiver_time (sender_id, receiver_id, create_time, id),
    INDEX idx_message_receiver_sender_time (receiver_id, sender_id, create_time, id),
    INDEX idx_message_resource (resource_id),
    INDEX idx_message_video_resource (video_resource_id),
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_receiver FOREIGN KEY (receiver_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_resource FOREIGN KEY (resource_id) REFERENCES file_resource(id) ON DELETE SET NULL,
    CONSTRAINT fk_message_video_resource FOREIGN KEY (video_resource_id) REFERENCES file_resource(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Direct message table';

CREATE TABLE conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Conversation ID',
    user1_id BIGINT NOT NULL COMMENT 'Smaller user ID in the pair',
    user2_id BIGINT NOT NULL COMMENT 'Larger user ID in the pair',
    last_message_id BIGINT NULL COMMENT 'Last message ID',
    last_message_time TIMESTAMP NULL COMMENT 'Last message time',
    unread_count_user1 INT NOT NULL DEFAULT 0 COMMENT 'Unread count for user1',
    unread_count_user2 INT NOT NULL DEFAULT 0 COMMENT 'Unread count for user2',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    CONSTRAINT uk_conversation_users UNIQUE (user1_id, user2_id),
    INDEX idx_conversation_user1_recent (user1_id, last_message_time, update_time, id),
    INDEX idx_conversation_user2_recent (user2_id, last_message_time, update_time, id),
    INDEX idx_conversation_last_message (last_message_id),
    CONSTRAINT fk_conversation_user1 FOREIGN KEY (user1_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_user2 FOREIGN KEY (user2_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_last_message FOREIGN KEY (last_message_id) REFERENCES message(id) ON DELETE SET NULL,
    CONSTRAINT chk_conversation_users_order CHECK (user1_id < user2_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='One-to-one conversation summary table';

INSERT INTO user (username, password_hash, avatar_url) VALUES
('alice', '$2a$10$dummy_hash_alice', 'https://example.com/avatars/alice.jpg'),
('bob', '$2a$10$dummy_hash_bob', 'https://example.com/avatars/bob.jpg');
