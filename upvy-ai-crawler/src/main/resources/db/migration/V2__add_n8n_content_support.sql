-- =============================================================================
-- V2: n8n Content Generator 지원을 위한 스키마 변경
-- =============================================================================
-- n8n에서 생성된 AI 콘텐츠를 pending_contents에 저장할 수 있도록 수정
-- ai_content_job_id를 nullable로 변경하고 source 컬럼 추가
-- =============================================================================

-- 1. FK 제약 조건 제거 (있는 경우)
SET @constraint_name = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'pending_contents'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
    LIMIT 1
);

SET @drop_fk = IF(@constraint_name IS NOT NULL,
    CONCAT('ALTER TABLE pending_contents DROP FOREIGN KEY ', @constraint_name),
    'SELECT 1');
PREPARE stmt FROM @drop_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. ai_content_job_id를 nullable로 변경
ALTER TABLE pending_contents
    MODIFY COLUMN ai_content_job_id BIGINT NULL;

-- 3. source 컬럼 추가 (콘텐츠 생성 소스 구분)
ALTER TABLE pending_contents
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'CRAWLER'
    AFTER ai_content_job_id;

-- 4. source 인덱스 추가
ALTER TABLE pending_contents
    ADD INDEX idx_pending_source (source);

-- 5. quiz 컬럼 추가 (n8n 생성 콘텐츠의 퀴즈 저장용)
ALTER TABLE pending_contents
    ADD COLUMN quiz JSON NULL
    AFTER tags;
