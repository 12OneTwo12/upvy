-- AI Content Crawler 시스템 계정 생성
-- 실행 시기: 크롤러 배포 전 1회 실행
-- 주의: 이미 존재하는 경우 중복 삽입 오류 발생 (의도적 설계)

-- 1. 시스템 사용자 계정 생성
INSERT INTO users (id, email, provider, provider_id, role, status, created_at, created_by, updated_at, updated_by)
VALUES (
    '00000000-0000-0000-0000-000000000001',  -- 고정 UUID (추적 용이)
    'ai-crawler@upvy.app',
    'SYSTEM',
    'ai-content-crawler',
    'USER',
    'ACTIVE',
    NOW(6),
    'system',
    NOW(6),
    'system'
);

-- 2. 시스템 사용자 프로필 생성
INSERT INTO user_profiles (user_id, nickname, profile_image_url, bio, follower_count, following_count, created_at, created_by, updated_at, updated_by)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Upvy AI',
    'https://upvy-images.s3.amazonaws.com/profile-images/8a8ea321-9fe3-4e52-af4e-aa6711a87ef2/profile_8a8ea321-9fe3-4e52-af4e-aa6711a87ef2_1764745438868.jpg',
    'AI가 큐레이션한 교육 숏폼 콘텐츠를 제공합니다.',
    0,
    0,
    NOW(6),
    'system',
    NOW(6),
    'system'
);

-- 3. 알림 설정 생성 (시스템 계정은 알림 수신 비활성화)
INSERT INTO notification_settings (user_id, all_notifications_enabled, like_notifications_enabled, comment_notifications_enabled, follow_notifications_enabled, created_at, created_by, updated_at, updated_by)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    FALSE,  -- 시스템 계정은 알림 비활성화
    FALSE,
    FALSE,
    FALSE,
    NOW(6),
    'system',
    NOW(6),
    'system'
);

-- 검증 쿼리 (실행 후 확인용)
-- SELECT u.id, u.email, u.provider, u.role, up.nickname, up.bio
-- FROM users u
-- JOIN user_profiles up ON u.id = up.user_id
-- WHERE u.id = '00000000-0000-0000-0000-000000000001';
