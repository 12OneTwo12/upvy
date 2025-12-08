-- =====================================================
-- V1: AI 크롤러 전체 테이블 생성
-- =====================================================

-- -----------------------------------------------------
-- 1. ai_content_job: AI 콘텐츠 생성 작업 테이블
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_content_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- YouTube 정보
    youtube_video_id VARCHAR(20) NOT NULL,
    youtube_channel_id VARCHAR(50),
    youtube_title VARCHAR(500),

    -- 작업 상태
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    -- 품질 점수
    quality_score INT,

    -- S3 저장 경로
    raw_video_s3_key VARCHAR(500),
    edited_video_s3_key VARCHAR(500),
    thumbnail_s3_key VARCHAR(500),

    -- STT 결과
    transcript TEXT,
    transcript_segments JSON COMMENT 'STT 타임스탬프 세그먼트 목록 (startTimeMs, endTimeMs, text)',

    -- LLM 생성 메타데이터
    generated_title VARCHAR(200),
    generated_description TEXT,
    generated_tags JSON,
    segments JSON COMMENT 'LLM이 추출한 핵심 구간 목록 (startTimeMs, endTimeMs, title, description, keywords)',
    category VARCHAR(50),
    difficulty VARCHAR(20),

    -- AI 제공자 정보
    llm_provider VARCHAR(20),
    llm_model VARCHAR(50),
    stt_provider VARCHAR(20),

    -- 에러 정보
    error_message TEXT,

    -- 검토 정보
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP NULL,
    rejection_reason TEXT,

    -- 게시된 콘텐츠 ID
    published_content_id BIGINT,

    -- Audit Trail
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'SYSTEM',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(50) DEFAULT 'SYSTEM',
    deleted_at TIMESTAMP NULL,

    -- 인덱스
    INDEX idx_job_status (status),
    INDEX idx_job_youtube_video_id (youtube_video_id),
    INDEX idx_job_created_at (created_at DESC),
    UNIQUE KEY uk_youtube_video_id (youtube_video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------
-- 2. pending_contents: AI 생성 콘텐츠 승인 대기열
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS pending_contents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 원본 Job 참조
    ai_content_job_id BIGINT NOT NULL,

    -- 콘텐츠 기본 정보
    title VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    difficulty VARCHAR(20),
    tags JSON,

    -- 미디어 정보
    video_s3_key VARCHAR(500) NOT NULL,
    thumbnail_s3_key VARCHAR(500),
    duration_seconds INT,
    width INT DEFAULT 1080,
    height INT DEFAULT 1920,

    -- 원본 YouTube 정보 (참고용)
    youtube_video_id VARCHAR(20),
    youtube_title VARCHAR(500),
    youtube_channel VARCHAR(200),

    -- 품질 정보
    quality_score INT NOT NULL,
    review_priority VARCHAR(20) NOT NULL,

    -- 승인 상태
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',

    -- 검토 정보
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP NULL,
    rejection_reason TEXT,

    -- 게시된 콘텐츠 ID (승인 후)
    published_content_id CHAR(36),

    -- Audit Trail
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'SYSTEM_AI_CRAWLER',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP NULL,

    -- 인덱스
    INDEX idx_pending_status (status),
    INDEX idx_pending_review_priority (review_priority),
    INDEX idx_pending_quality_score (quality_score DESC),
    INDEX idx_pending_created_at (created_at DESC),

    CONSTRAINT fk_pending_ai_content_job
        FOREIGN KEY (ai_content_job_id) REFERENCES ai_content_job(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------
-- 3. backoffice_users: 백오피스 관리자 계정
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS backoffice_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ADMIN',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    -- Audit Trail
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL,

    INDEX idx_backoffice_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------
-- 4. 기본 관리자 계정 생성
-- 비밀번호: password123 (BCrypt 해시)
-- -----------------------------------------------------
INSERT INTO backoffice_users (username, password, name, role)
VALUES ('admin', '$2a$10$cGkFIfp7ByGHtiGTWpU8Jea6.wZpmDmhl9aIazE3/INB3kP3vUtYO', '관리자', 'ADMIN')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;
