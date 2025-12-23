-- ============================================
-- Tag Data Migration Script
-- ============================================
-- 목적: content_metadata.tags (JSON) → tags + content_tags 테이블로 마이그레이션
--
-- 실행 방법:
-- mysql -h [HOST] -u [USER] -p [DATABASE] < migrate-tags.sql
--
-- 마이그레이션 절차:
-- 1. content_metadata에서 모든 tags JSON 추출
-- 2. 유니크한 태그명 추출 → tags 테이블 INSERT
-- 3. content_id + tag_id 매핑 → content_tags 테이블 INSERT
-- 4. tags.usage_count 계산 (각 태그가 몇 개 콘텐츠에 사용되었는지)
-- ============================================

SET @now = NOW(6);
SET @system_user = 'SYSTEM';

-- ============================================
-- Step 1: tags 테이블에 유니크한 태그명 INSERT
-- ============================================
-- content_metadata.tags JSON에서 모든 태그를 추출하여 tags 테이블에 삽입
-- JSON_TABLE을 사용하여 JSON 배열을 행으로 변환
-- ============================================

INSERT IGNORE INTO tags (name, normalized_name, usage_count, created_at, created_by, updated_at, updated_by)
SELECT DISTINCT
    TRIM(tag_name) AS name,
    LOWER(TRIM(REPLACE(tag_name, '#', ''))) AS normalized_name,
    0 AS usage_count,
    @now AS created_at,
    @system_user AS created_by,
    @now AS updated_at,
    @system_user AS updated_by
FROM content_metadata cm
         CROSS JOIN JSON_TABLE(
        cm.tags,
        '$[*]' COLUMNS (
            tag_name VARCHAR(50) PATH '$'
            )
                    ) AS jt
WHERE cm.deleted_at IS NULL
  AND cm.tags IS NOT NULL
  AND JSON_LENGTH(cm.tags) > 0
  AND TRIM(tag_name) != '';

-- ============================================
-- Step 2: content_tags 관계 테이블 INSERT
-- ============================================
-- content_id와 tag_id 매핑을 생성
-- ============================================

INSERT IGNORE INTO content_tags (content_id, tag_id, created_at, created_by, updated_at, updated_by)
SELECT DISTINCT
    cm.content_id,
    t.id AS tag_id,
    @now AS created_at,
    @system_user AS created_by,
    @now AS updated_at,
    @system_user AS updated_by
FROM content_metadata cm
         CROSS JOIN JSON_TABLE(
        cm.tags,
        '$[*]' COLUMNS (
            tag_name VARCHAR(50) PATH '$'
            )
                    ) AS jt
         INNER JOIN tags t
                    ON t.normalized_name = LOWER(TRIM(REPLACE(jt.tag_name, '#', '')))
         INNER JOIN contents c
                    ON c.id = cm.content_id
WHERE cm.deleted_at IS NULL
  AND c.deleted_at IS NULL
  AND c.status = 'PUBLISHED'
  AND cm.tags IS NOT NULL
  AND JSON_LENGTH(cm.tags) > 0
  AND TRIM(jt.tag_name) != ''
  AND t.deleted_at IS NULL;

-- ============================================
-- Step 3: usage_count 계산 및 업데이트
-- ============================================
-- 각 태그가 몇 개의 콘텐츠에 사용되었는지 계산
-- ============================================

UPDATE tags t
    INNER JOIN (
    SELECT
        tag_id,
        COUNT(DISTINCT content_id) AS cnt
    FROM content_tags
    WHERE deleted_at IS NULL
    GROUP BY tag_id
) AS usage_counts
ON t.id = usage_counts.tag_id
SET t.usage_count = usage_counts.cnt,
    t.updated_at = @now,
    t.updated_by = @system_user
WHERE t.deleted_at IS NULL;

-- ============================================
-- 마이그레이션 완료 확인 쿼리
-- ============================================

-- 1. 총 태그 수
SELECT COUNT(*) AS total_tags FROM tags WHERE deleted_at IS NULL;

-- 2. 총 content_tags 관계 수
SELECT COUNT(*) AS total_content_tags FROM content_tags WHERE deleted_at IS NULL;

-- 3. 인기 태그 Top 10
SELECT
    t.name,
    t.normalized_name,
    t.usage_count
FROM tags t
WHERE t.deleted_at IS NULL
ORDER BY t.usage_count DESC
LIMIT 10;

-- 4. content_metadata.tags와 content_tags 개수 비교 검증
SELECT
    cm.content_id,
    cm.title,
    JSON_LENGTH(cm.tags) AS json_tag_count,
    (SELECT COUNT(*) FROM content_tags ct WHERE ct.content_id = cm.content_id AND ct.deleted_at IS NULL) AS table_tag_count
FROM content_metadata cm
         INNER JOIN contents c ON cm.content_id = c.id
WHERE cm.deleted_at IS NULL
  AND c.deleted_at IS NULL
  AND cm.tags IS NOT NULL
  AND JSON_LENGTH(cm.tags) > 0
HAVING json_tag_count != table_tag_count
LIMIT 10;
