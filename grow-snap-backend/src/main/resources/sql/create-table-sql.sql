-- Users Table
CREATE TABLE IF NOT EXISTS users (
    id CHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    provider VARCHAR(50) NOT NULL DEFAULT 'GOOGLE',
    provider_id VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT unique_provider_user UNIQUE (provider, provider_id)
);

CREATE INDEX IF NOT EXISTS idx_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_provider_id ON users(provider_id);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_deleted_at ON users(deleted_at);

-- User Status History Table (Audit Trail)
CREATE TABLE IF NOT EXISTS user_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NULL,
    metadata JSON NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(36) NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_status_history_user_id ON user_status_history(user_id);
CREATE INDEX idx_user_status_history_changed_at ON user_status_history(changed_at);

-- User Profiles Table
CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL UNIQUE,
    nickname VARCHAR(20) NOT NULL UNIQUE,
    profile_image_url VARCHAR(500),
    bio VARCHAR(500),
    follower_count INT DEFAULT 0,
    following_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_nickname ON user_profiles(nickname);
CREATE INDEX IF NOT EXISTS idx_user_id ON user_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_profile_deleted_at ON user_profiles(deleted_at);

-- Follows Table (팔로우 관계)
CREATE TABLE IF NOT EXISTS follows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    follower_id CHAR(36) NOT NULL,
    following_id CHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (following_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_follow UNIQUE (follower_id, following_id)
);

CREATE INDEX IF NOT EXISTS idx_follower ON follows(follower_id);
CREATE INDEX IF NOT EXISTS idx_following ON follows(following_id);
CREATE INDEX IF NOT EXISTS idx_follow_deleted_at ON follows(deleted_at);

-- Contents Table (비디오/사진 콘텐츠)
CREATE TABLE IF NOT EXISTS contents (
    id CHAR(36) PRIMARY KEY,
    creator_id CHAR(36) NOT NULL,
    content_type VARCHAR(20) NOT NULL,  -- VIDEO, PHOTO
    url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500) NOT NULL,
    duration INT NULL,  -- 비디오 길이 (초), 사진인 경우 NULL
    width INT NOT NULL,
    height INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, PUBLISHED, DELETED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_creator_id ON contents(creator_id);
CREATE INDEX IF NOT EXISTS idx_content_type ON contents(content_type);
CREATE INDEX IF NOT EXISTS idx_status ON contents(status);
CREATE INDEX IF NOT EXISTS idx_content_created_at ON contents(created_at);
CREATE INDEX IF NOT EXISTS idx_content_deleted_at ON contents(deleted_at);

-- Content Photos Table (사진 콘텐츠의 사진 목록 - 1:N 관계)
CREATE TABLE IF NOT EXISTS content_photos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id CHAR(36) NOT NULL,
    photo_url VARCHAR(500) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,  -- 사진 표시 순서 (인스타그램 스타일)
    width INT NOT NULL,
    height INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_photo_content_id ON content_photos(content_id);
CREATE INDEX IF NOT EXISTS idx_photo_display_order ON content_photos(content_id, display_order);
CREATE INDEX IF NOT EXISTS idx_photo_deleted_at ON content_photos(deleted_at);

-- Content Metadata Table (콘텐츠 메타데이터)
CREATE TABLE IF NOT EXISTS content_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id CHAR(36) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    tags JSON,  -- ["tag1", "tag2", "tag3"]
    difficulty_level VARCHAR(20),  -- BEGINNER, INTERMEDIATE, ADVANCED
    language VARCHAR(10) NOT NULL DEFAULT 'ko',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_metadata_content_id ON content_metadata(content_id);
CREATE INDEX IF NOT EXISTS idx_category ON content_metadata(category);
CREATE INDEX IF NOT EXISTS idx_metadata_deleted_at ON content_metadata(deleted_at);

-- Content Interactions Table (인터랙션 카운트)
CREATE TABLE IF NOT EXISTS content_interactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id CHAR(36) NOT NULL UNIQUE,
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    save_count INT DEFAULT 0,
    share_count INT DEFAULT 0,
    view_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_interaction_content_id ON content_interactions(content_id);
CREATE INDEX IF NOT EXISTS idx_like_count ON content_interactions(like_count);
CREATE INDEX IF NOT EXISTS idx_view_count ON content_interactions(view_count);
CREATE INDEX IF NOT EXISTS idx_interaction_deleted_at ON content_interactions(deleted_at);

-- Content Subtitles Table (자막)
CREATE TABLE IF NOT EXISTS content_subtitles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id CHAR(36) NOT NULL,
    language VARCHAR(10) NOT NULL,
    subtitle_url VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE,
    CONSTRAINT unique_content_language UNIQUE (content_id, language)
);

CREATE INDEX IF NOT EXISTS idx_subtitle_content_id ON content_subtitles(content_id);
CREATE INDEX IF NOT EXISTS idx_subtitle_language ON content_subtitles(language);
CREATE INDEX IF NOT EXISTS idx_subtitle_deleted_at ON content_subtitles(deleted_at);

-- User View History Table (사용자 시청 기록)
CREATE TABLE IF NOT EXISTS user_view_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    content_id CHAR(36) NOT NULL,
    watched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    watched_duration INT DEFAULT 0,  -- 시청한 시간 (초)
    completion_rate INT DEFAULT 0,  -- 0-100
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_view_user_id ON user_view_history(user_id);
CREATE INDEX IF NOT EXISTS idx_view_content_id ON user_view_history(content_id);
CREATE INDEX IF NOT EXISTS idx_watched_at ON user_view_history(watched_at);
CREATE INDEX IF NOT EXISTS idx_view_deleted_at ON user_view_history(deleted_at);
CREATE INDEX IF NOT EXISTS idx_user_watched ON user_view_history(user_id, watched_at);

-- User Content Interactions Table (사용자별 인터랙션 기록)
-- 협업 필터링을 위한 사용자-콘텐츠 인터랙션 저장
CREATE TABLE IF NOT EXISTS user_content_interactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    content_id CHAR(36) NOT NULL,
    interaction_type VARCHAR(20) NOT NULL,  -- LIKE, SAVE, SHARE
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_content_interaction UNIQUE (user_id, content_id, interaction_type)
);

CREATE INDEX IF NOT EXISTS idx_user_interaction_user_id ON user_content_interactions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_interaction_content_id ON user_content_interactions(content_id);
CREATE INDEX IF NOT EXISTS idx_user_interaction_type ON user_content_interactions(interaction_type);
CREATE INDEX IF NOT EXISTS idx_user_interaction_deleted_at ON user_content_interactions(deleted_at);
CREATE INDEX IF NOT EXISTS idx_user_interaction_composite ON user_content_interactions(user_id, content_id);

-- Comments Table (댓글 및 답글, 최대 depth 2)
CREATE TABLE IF NOT EXISTS comments (
    id CHAR(36) PRIMARY KEY,
    content_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    parent_comment_id CHAR(36) NULL,  -- 답글인 경우 부모 댓글 ID, NULL이면 최상위 댓글
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_comment_id) REFERENCES comments(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_comment_content_id ON comments(content_id);
CREATE INDEX IF NOT EXISTS idx_comment_user_id ON comments(user_id);
CREATE INDEX IF NOT EXISTS idx_comment_parent_id ON comments(parent_comment_id);
CREATE INDEX IF NOT EXISTS idx_comment_deleted_at ON comments(deleted_at);
CREATE INDEX IF NOT EXISTS idx_comment_created_at ON comments(created_at);

-- User Likes Table (사용자별 좋아요 상태)
CREATE TABLE IF NOT EXISTS user_likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    content_id CHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    deleted_at_unix BIGINT NOT NULL DEFAULT 0 COMMENT '0=active, unix_timestamp=deleted',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_like UNIQUE (user_id, content_id, deleted_at_unix)
);

CREATE INDEX IF NOT EXISTS idx_user_like_user_id ON user_likes(user_id);
CREATE INDEX IF NOT EXISTS idx_user_like_content_id ON user_likes(content_id);
CREATE INDEX IF NOT EXISTS idx_user_like_deleted_at ON user_likes(deleted_at);
CREATE INDEX IF NOT EXISTS idx_user_like_composite ON user_likes(user_id, content_id);

-- User Saves Table (사용자별 저장 상태)
CREATE TABLE IF NOT EXISTS user_saves (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    content_id CHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    deleted_at_unix BIGINT NOT NULL DEFAULT 0 COMMENT '0=active, unix_timestamp=deleted',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_save UNIQUE (user_id, content_id, deleted_at_unix)
);

CREATE INDEX IF NOT EXISTS idx_user_save_user_id ON user_saves(user_id);
CREATE INDEX IF NOT EXISTS idx_user_save_content_id ON user_saves(content_id);
CREATE INDEX IF NOT EXISTS idx_user_save_deleted_at ON user_saves(deleted_at);
CREATE INDEX IF NOT EXISTS idx_user_save_composite ON user_saves(user_id, content_id);
CREATE INDEX IF NOT EXISTS idx_user_save_created_at ON user_saves(created_at);

-- User Comment Likes Table (댓글별 좋아요 상태)
CREATE TABLE IF NOT EXISTS user_comment_likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    comment_id CHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    deleted_at_unix BIGINT NOT NULL DEFAULT 0 COMMENT '0=active, unix_timestamp=deleted',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_comment_like UNIQUE (user_id, comment_id, deleted_at_unix)
);

CREATE INDEX IF NOT EXISTS idx_user_comment_like_comment_id ON user_comment_likes(comment_id);
CREATE INDEX IF NOT EXISTS idx_user_comment_like_deleted_at ON user_comment_likes(deleted_at);
CREATE INDEX IF NOT EXISTS idx_user_comment_like_composite ON user_comment_likes(user_id, comment_id);

-- Reports Table (신고)
CREATE TABLE IF NOT EXISTS reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id CHAR(36) NOT NULL,
    target_type VARCHAR(20) NOT NULL,  -- CONTENT, COMMENT, USER
    target_id CHAR(36) NOT NULL,
    reason VARCHAR(50) NOT NULL,  -- SPAM, HARASSMENT, INAPPROPRIATE, COPYRIGHT, OTHER
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_report_reporter_id ON reports(reporter_id);
CREATE INDEX IF NOT EXISTS idx_report_target ON reports(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_report_status ON reports(status);
CREATE INDEX IF NOT EXISTS idx_report_deleted_at ON reports(deleted_at);
CREATE INDEX IF NOT EXISTS idx_report_created_at ON reports(created_at);

-- Search History Table (검색 기록)
CREATE TABLE IF NOT EXISTS search_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    keyword VARCHAR(100) NOT NULL,
    search_type VARCHAR(20) NOT NULL,  -- CONTENT, USER
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NULL,
    deleted_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_search_history_user_id ON search_history(user_id);
CREATE INDEX IF NOT EXISTS idx_search_history_keyword ON search_history(keyword);
CREATE INDEX IF NOT EXISTS idx_search_history_created_at ON search_history(created_at);
CREATE INDEX IF NOT EXISTS idx_search_history_composite ON search_history(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_search_history_deleted_at ON search_history(deleted_at);
