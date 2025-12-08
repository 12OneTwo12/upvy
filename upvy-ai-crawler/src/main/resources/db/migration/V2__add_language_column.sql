-- =====================================================
-- V2: 다국어 지원을 위한 language 컬럼 추가
-- =====================================================

-- -----------------------------------------------------
-- ai_content_job 테이블에 language 컬럼 추가
-- -----------------------------------------------------
ALTER TABLE ai_content_job
    ADD COLUMN language VARCHAR(5) DEFAULT 'ko' AFTER stt_provider,
    ADD INDEX idx_job_language (language);

-- -----------------------------------------------------
-- pending_contents 테이블에 language 컬럼 추가
-- -----------------------------------------------------
ALTER TABLE pending_contents
    ADD COLUMN language VARCHAR(5) DEFAULT 'ko' NOT NULL AFTER quality_score,
    ADD INDEX idx_pending_language (language);
