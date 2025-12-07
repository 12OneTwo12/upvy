-- Drop all tables first to ensure clean state for each test context
-- Disable foreign key checks for clean drop
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS expo_push_tokens;
DROP TABLE IF EXISTS notification_settings;
DROP TABLE IF EXISTS search_history;
DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS user_blocks;
DROP TABLE IF EXISTS content_blocks;
DROP TABLE IF EXISTS user_comment_likes;
DROP TABLE IF EXISTS user_content_interactions;
DROP TABLE IF EXISTS user_likes;
DROP TABLE IF EXISTS user_saves;
DROP TABLE IF EXISTS user_view_history;
DROP TABLE IF EXISTS comments;
DROP TABLE IF EXISTS content_subtitles;
DROP TABLE IF EXISTS content_interactions;
DROP TABLE IF EXISTS content_metadata;
DROP TABLE IF EXISTS content_photos;
DROP TABLE IF EXISTS contents;
DROP TABLE IF EXISTS follows;
DROP TABLE IF EXISTS user_profiles;
DROP TABLE IF EXISTS user_status_history;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- Users Table
CREATE TABLE users (
    id CHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    provider VARCHAR(50) NOT NULL DEFAULT 'GOOGLE',
    provider_id VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT unique_provider_user UNIQUE (provider, provider_id)
);

CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_provider_id ON users(provider_id);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_deleted_at ON users(deleted_at);

-- User Profiles Table
CREATE TABLE user_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    nickname VARCHAR(20) NOT NULL,
    profile_image_url VARCHAR(500),
    bio VARCHAR(500),
    follower_count INT DEFAULT 0,
    following_count INT DEFAULT 0,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_at_unix BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_profile UNIQUE (user_id, deleted_at_unix),
    CONSTRAINT unique_nickname UNIQUE (nickname, deleted_at_unix)
);

CREATE INDEX idx_nickname ON user_profiles(nickname);
CREATE INDEX idx_user_id ON user_profiles(user_id);
CREATE INDEX idx_profile_deleted_at ON user_profiles(deleted_at);

-- User Status History Table
CREATE TABLE user_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NULL,
    metadata JSON NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    changed_by VARCHAR(36) NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_status_history_user_id ON user_status_history(user_id);
CREATE INDEX idx_user_status_history_changed_at ON user_status_history(changed_at);

-- Follows Table
CREATE TABLE follows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    follower_id CHAR(36) NOT NULL,
    following_id CHAR(36) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_at_unix BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (following_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_follow UNIQUE (follower_id, following_id, deleted_at_unix)
);

CREATE INDEX idx_follower ON follows(follower_id);
CREATE INDEX idx_following ON follows(following_id);
CREATE INDEX idx_follow_deleted_at ON follows(deleted_at);
CREATE INDEX idx_follow_composite ON follows(follower_id, following_id);

-- Contents Table
CREATE TABLE contents (
    id CHAR(36) PRIMARY KEY,
    creator_id CHAR(36) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500) NOT NULL,
    duration INT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_creator_id ON contents(creator_id);
CREATE INDEX idx_content_type ON contents(content_type);
CREATE INDEX idx_status ON contents(status);
CREATE INDEX idx_content_created_at ON contents(created_at);
CREATE INDEX idx_content_deleted_at ON contents(deleted_at);

-- Content Photos Table
CREATE TABLE content_photos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id CHAR(36) NOT NULL,
    photo_url VARCHAR(500) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    width INT NOT NULL,
    height INT NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE
);

CREATE INDEX idx_photo_content_id ON content_photos(content_id);
CREATE INDEX idx_photo_display_order ON content_photos(content_id, display_order);
CREATE INDEX idx_photo_deleted_at ON content_photos(deleted_at);

-- Content Metadata Table
CREATE TABLE content_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id CHAR(36) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    tags JSON,
    difficulty_level VARCHAR(20),
    language VARCHAR(10) NOT NULL DEFAULT 'ko',
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE
);

CREATE INDEX idx_metadata_content_id ON content_metadata(content_id);
CREATE INDEX idx_category ON content_metadata(category);
CREATE INDEX idx_metadata_deleted_at ON content_metadata(deleted_at);

-- Content Interactions Table
CREATE TABLE content_interactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id CHAR(36) NOT NULL UNIQUE,
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    save_count INT DEFAULT 0,
    share_count INT DEFAULT 0,
    view_count INT DEFAULT 0,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE
);

CREATE INDEX idx_interaction_content_id ON content_interactions(content_id);
CREATE INDEX idx_like_count ON content_interactions(like_count);
CREATE INDEX idx_view_count ON content_interactions(view_count);
CREATE INDEX idx_interaction_deleted_at ON content_interactions(deleted_at);

-- Content Subtitles Table
CREATE TABLE content_subtitles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id CHAR(36) NOT NULL,
    language VARCHAR(10) NOT NULL,
    subtitle_url VARCHAR(500) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE,
    CONSTRAINT unique_content_language UNIQUE (content_id, language)
);

CREATE INDEX idx_subtitle_content_id ON content_subtitles(content_id);
CREATE INDEX idx_subtitle_language ON content_subtitles(language);
CREATE INDEX idx_subtitle_deleted_at ON content_subtitles(deleted_at);

-- User View History Table
CREATE TABLE user_view_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    content_id CHAR(36) NOT NULL,
    watched_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    watched_duration INT DEFAULT 0,
    completion_rate INT DEFAULT 0,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE
);

CREATE INDEX idx_view_user_id ON user_view_history(user_id);
CREATE INDEX idx_view_content_id ON user_view_history(content_id);
CREATE INDEX idx_watched_at ON user_view_history(watched_at);
CREATE INDEX idx_view_deleted_at ON user_view_history(deleted_at);
CREATE INDEX idx_user_watched ON user_view_history(user_id, watched_at);

-- User Content Interactions Table
CREATE TABLE user_content_interactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    content_id CHAR(36) NOT NULL,
    interaction_type VARCHAR(20) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_content_interaction UNIQUE (user_id, content_id, interaction_type)
);

CREATE INDEX idx_user_interaction_user_id ON user_content_interactions(user_id);
CREATE INDEX idx_user_interaction_content_id ON user_content_interactions(content_id);
CREATE INDEX idx_user_interaction_type ON user_content_interactions(interaction_type);
CREATE INDEX idx_user_interaction_deleted_at ON user_content_interactions(deleted_at);
CREATE INDEX idx_user_interaction_composite ON user_content_interactions(user_id, content_id);

-- Comments Table
CREATE TABLE comments (
    id CHAR(36) PRIMARY KEY,
    content_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    parent_comment_id CHAR(36) NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_comment_id) REFERENCES comments(id) ON DELETE CASCADE
);

CREATE INDEX idx_comment_content_id ON comments(content_id);
CREATE INDEX idx_comment_user_id ON comments(user_id);
CREATE INDEX idx_comment_parent_id ON comments(parent_comment_id);
CREATE INDEX idx_comment_deleted_at ON comments(deleted_at);
CREATE INDEX idx_comment_created_at ON comments(created_at);

-- User Likes Table
CREATE TABLE user_likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    content_id CHAR(36) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_at_unix BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_like UNIQUE (user_id, content_id, deleted_at_unix)
);

CREATE INDEX idx_user_like_user_id ON user_likes(user_id);
CREATE INDEX idx_user_like_content_id ON user_likes(content_id);
CREATE INDEX idx_user_like_deleted_at ON user_likes(deleted_at);
CREATE INDEX idx_user_like_composite ON user_likes(user_id, content_id);

-- User Saves Table
CREATE TABLE user_saves (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    content_id CHAR(36) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_at_unix BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_save UNIQUE (user_id, content_id, deleted_at_unix)
);

CREATE INDEX idx_user_save_user_id ON user_saves(user_id);
CREATE INDEX idx_user_save_content_id ON user_saves(content_id);
CREATE INDEX idx_user_save_deleted_at ON user_saves(deleted_at);
CREATE INDEX idx_user_save_composite ON user_saves(user_id, content_id);
CREATE INDEX idx_user_save_created_at ON user_saves(created_at);

-- User Comment Likes Table
CREATE TABLE user_comment_likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    comment_id CHAR(36) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_at_unix BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_comment_like UNIQUE (user_id, comment_id, deleted_at_unix)
);

CREATE INDEX idx_user_comment_like_comment_id ON user_comment_likes(comment_id);
CREATE INDEX idx_user_comment_like_deleted_at ON user_comment_likes(deleted_at);
CREATE INDEX idx_user_comment_like_composite ON user_comment_likes(user_id, comment_id);

-- Reports Table
CREATE TABLE reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id CHAR(36) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id CHAR(36) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_report_reporter_id ON reports(reporter_id);
CREATE INDEX idx_report_target ON reports(target_type, target_id);
CREATE INDEX idx_report_status ON reports(status);
CREATE INDEX idx_report_deleted_at ON reports(deleted_at);
CREATE INDEX idx_report_created_at ON reports(created_at);

-- Search History Table
CREATE TABLE search_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    keyword VARCHAR(100) NOT NULL,
    search_type VARCHAR(20) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_search_history_user_id ON search_history(user_id);
CREATE INDEX idx_search_history_keyword ON search_history(keyword);
CREATE INDEX idx_search_history_created_at ON search_history(created_at);
CREATE INDEX idx_search_history_composite ON search_history(user_id, created_at);
CREATE INDEX idx_search_history_deleted_at ON search_history(deleted_at);

-- User Blocks Table
CREATE TABLE user_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    blocker_id CHAR(36) NOT NULL,
    blocked_id CHAR(36) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_block UNIQUE (blocker_id, blocked_id)
);

CREATE INDEX idx_user_block_blocker_id ON user_blocks(blocker_id);
CREATE INDEX idx_user_block_blocked_id ON user_blocks(blocked_id);
CREATE INDEX idx_user_block_deleted_at ON user_blocks(deleted_at);

-- Content Blocks Table
CREATE TABLE content_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    content_id CHAR(36) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE,
    CONSTRAINT unique_content_block UNIQUE (user_id, content_id)
);

CREATE INDEX idx_content_block_user_id ON content_blocks(user_id);
CREATE INDEX idx_content_block_content_id ON content_blocks(content_id);
CREATE INDEX idx_content_block_deleted_at ON content_blocks(deleted_at);

-- Notification Settings Table
CREATE TABLE notification_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL UNIQUE,
    all_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    like_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    comment_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    follow_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_notification_settings_user_id ON notification_settings(user_id);
CREATE INDEX idx_notification_settings_deleted_at ON notification_settings(deleted_at);

-- Push Tokens Table (다양한 푸시 제공자 지원)
CREATE TABLE push_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    token VARCHAR(500) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    device_type VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    provider VARCHAR(20) NOT NULL DEFAULT 'EXPO',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_push_user_device UNIQUE (user_id, device_id)
);

CREATE INDEX idx_push_tokens_user_id ON push_tokens(user_id);
CREATE INDEX idx_push_tokens_token ON push_tokens(token);
CREATE INDEX idx_push_tokens_device_id ON push_tokens(device_id);
CREATE INDEX idx_push_tokens_provider ON push_tokens(provider);
CREATE INDEX idx_push_tokens_deleted_at ON push_tokens(deleted_at);

-- Notifications Table
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(500) NOT NULL,
    data JSON NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    actor_id CHAR(36) NULL,
    target_type VARCHAR(30) NULL,
    target_id CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);
CREATE INDEX idx_notifications_deleted_at ON notifications(deleted_at);
CREATE INDEX idx_notifications_type ON notifications(type);
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_delivery_status ON notifications(delivery_status);

-- Push Notification Logs Table (Append Only)
CREATE TABLE push_notification_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    push_token_id BIGINT NULL,
    provider VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(255) NULL,
    error_code VARCHAR(50) NULL,
    error_message VARCHAR(500) NULL,
    attempt_count INT NOT NULL DEFAULT 1,
    sent_at DATETIME(6) NOT NULL,
    delivered_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE,
    FOREIGN KEY (push_token_id) REFERENCES push_tokens(id) ON DELETE SET NULL
);

CREATE INDEX idx_push_logs_notification_id ON push_notification_logs(notification_id);
CREATE INDEX idx_push_logs_push_token_id ON push_notification_logs(push_token_id);
CREATE INDEX idx_push_logs_status ON push_notification_logs(status);
CREATE INDEX idx_push_logs_sent_at ON push_notification_logs(sent_at);
CREATE INDEX idx_push_logs_provider ON push_notification_logs(provider);
