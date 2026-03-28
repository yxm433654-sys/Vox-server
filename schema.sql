-- ============================================
-- 跨平台动态图片兼容系统 - 数据库设计
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS chatdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE chatdb;

-- 用户表
CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    avatar_url VARCHAR(255) COMMENT '头像URL',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    status TINYINT DEFAULT 1 COMMENT '状态: 1-正常 0-禁用',
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 文件资源表
CREATE TABLE file_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '文件ID',
    original_name VARCHAR(255) COMMENT '原始文件名',
    storage_path VARCHAR(512) NOT NULL COMMENT 'MinIO存储路径',
    file_type ENUM('IMAGE', 'VIDEO', 'DYNAMIC_COVER', 'DYNAMIC_VIDEO') NOT NULL COMMENT '文件类型',
    source_type VARCHAR(32) COMMENT '来源类型: iOS_LivePhoto, Android_MotionPhoto, Normal',
    mime_type VARCHAR(64) COMMENT 'MIME类型',
    file_size BIGINT COMMENT '文件大小(字节)',
    width INT COMMENT '图片/视频宽度',
    height INT COMMENT '图片/视频高度',
    duration FLOAT COMMENT '视频时长(秒)',
    cover_time FLOAT COMMENT 'Live Photo封面时间点',
    related_file_id BIGINT COMMENT '关联文件ID(封面对应视频,视频对应封面)',
    metadata_json TEXT COMMENT '额外元数据JSON',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    uploader_id BIGINT COMMENT '上传者ID',
    INDEX idx_storage_path (storage_path(255)),
    INDEX idx_related_file (related_file_id),
    INDEX idx_uploader (uploader_id),
    INDEX idx_source_type (source_type),
    FOREIGN KEY (uploader_id) REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件资源表';

-- 消息表
CREATE TABLE message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    receiver_id BIGINT NOT NULL COMMENT '接收者ID',
    type ENUM('TEXT', 'IMAGE', 'VIDEO', 'DYNAMIC_PHOTO') NOT NULL COMMENT '消息类型',
    content TEXT COMMENT '文本内容',
    resource_id BIGINT COMMENT '关联资源ID(封面图)',
    video_resource_id BIGINT COMMENT '关联视频资源ID',
    status VARCHAR(16) DEFAULT 'SENT' COMMENT '消息状态: SENT, DELIVERED, READ',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    read_time TIMESTAMP NULL COMMENT '阅读时间',
    INDEX idx_sender (sender_id, create_time),
    INDEX idx_receiver (receiver_id, create_time),
    INDEX idx_conversation (sender_id, receiver_id, create_time),
    FOREIGN KEY (sender_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (resource_id) REFERENCES file_resource(id) ON DELETE SET NULL,
    FOREIGN KEY (video_resource_id) REFERENCES file_resource(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- 会话表(可选,用于优化会话列表查询)
CREATE TABLE conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    user1_id BIGINT NOT NULL COMMENT '用户1 ID',
    user2_id BIGINT NOT NULL COMMENT '用户2 ID',
    last_message_id BIGINT COMMENT '最后一条消息ID',
    last_message_time TIMESTAMP COMMENT '最后消息时间',
    unread_count_user1 INT DEFAULT 0 COMMENT '用户1未读数',
    unread_count_user2 INT DEFAULT 0 COMMENT '用户2未读数',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_users (user1_id, user2_id),
    INDEX idx_user1 (user1_id, last_message_time),
    INDEX idx_user2 (user2_id, last_message_time),
    FOREIGN KEY (user1_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (user2_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (last_message_id) REFERENCES message(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 初始化测试数据
INSERT INTO user (username, password_hash, avatar_url) VALUES 
('alice', '$2a$10$dummy_hash_alice', 'https://example.com/avatars/alice.jpg'),
('bob', '$2a$10$dummy_hash_bob', 'https://example.com/avatars/bob.jpg');
