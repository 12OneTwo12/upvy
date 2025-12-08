# AI 크롤러 백오피스 구현 계획서

## 1. 개요

### 1.1 목적
AI 크롤러가 생성한 콘텐츠를 관리자가 검토/승인할 수 있는 백오피스 시스템을 구현합니다.
승인된 콘텐츠만 백엔드 `contents` 테이블에 INSERT되어 앱에 노출됩니다.

### 1.2 핵심 흐름
```
[AI 크롤러 배치]
     ↓
[품질 점수 70점 이상]
     ↓
[pending_contents 테이블에 INSERT] ← PENDING_REVIEW 상태
     ↓
[관리자 백오피스에서 검토]
     ↓
[승인] → [백엔드 contents + content_metadata INSERT] → PENDING 상태
[거절] → [pending_contents 상태를 REJECTED로 UPDATE]
```

### 1.3 기술 스택
- **백오피스 UI**: Thymeleaf + Bootstrap 5
- **인증**: Spring Security (간단한 폼 로그인)
- **DB**: 기존 MySQL (크롤러와 동일 DB)

---

## 2. 데이터베이스 설계

### 2.1 pending_contents 테이블 (신규)

AI 크롤러가 생성한 콘텐츠의 승인 대기열입니다.

```sql
CREATE TABLE pending_contents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 원본 Job 참조
    ai_content_job_id BIGINT NOT NULL,

    -- 콘텐츠 기본 정보
    title VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    difficulty VARCHAR(20),
    tags JSON,  -- ["tag1", "tag2", ...]

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
    review_priority VARCHAR(20) NOT NULL,  -- HIGH, NORMAL, LOW

    -- 승인 상태
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    -- PENDING_REVIEW: 검토 대기
    -- APPROVED: 승인됨 (contents에 INSERT 완료)
    -- REJECTED: 거절됨
    -- PUBLISHED: 게시됨 (contents.status = PUBLISHED)

    -- 검토 정보
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP NULL,
    rejection_reason TEXT,

    -- 게시된 콘텐츠 ID (승인 후)
    published_content_id CHAR(36),  -- UUID

    -- Audit Trail
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'SYSTEM_AI_CRAWLER',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP NULL,

    -- 인덱스
    INDEX idx_status (status),
    INDEX idx_review_priority (review_priority),
    INDEX idx_quality_score (quality_score DESC),
    INDEX idx_created_at (created_at DESC),

    FOREIGN KEY (ai_content_job_id) REFERENCES ai_content_jobs(id)
);
```

### 2.2 backoffice_users 테이블 (신규)

백오피스 관리자 계정입니다.

```sql
CREATE TABLE backoffice_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,  -- BCrypt 해시
    name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ADMIN',  -- ADMIN, REVIEWER
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    -- Audit Trail
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL
);

-- 기본 관리자 계정 (비밀번호: admin123 → BCrypt 해시)
INSERT INTO backoffice_users (username, password, name, role)
VALUES ('admin', '$2a$10$...', '관리자', 'ADMIN');
```

---

## 3. 백오피스 기능 설계

### 3.1 페이지 구조

```
/backoffice
├── /login                  # 로그인 페이지
├── /dashboard              # 대시보드 (통계)
├── /pending                # 승인 대기 목록
│   └── /{id}              # 콘텐츠 상세/승인/거절
├── /approved               # 승인된 콘텐츠 목록
├── /rejected               # 거절된 콘텐츠 목록
└── /settings               # 설정 (품질 기준 등)
```

### 3.2 대시보드

| 항목 | 설명 |
|------|------|
| 오늘 생성된 콘텐츠 | 오늘 배치에서 생성된 수 |
| 승인 대기 | PENDING_REVIEW 상태 수 |
| 이번 주 승인/거절 | 통계 |
| 평균 품질 점수 | 전체 평균 |
| 카테고리별 분포 | 차트 |

### 3.3 승인 대기 목록

| 컬럼 | 설명 |
|------|------|
| 썸네일 | 미리보기 이미지 |
| 제목 | AI 생성 제목 |
| 카테고리 | 분류 |
| 품질 점수 | 0-100 |
| 우선순위 | HIGH/NORMAL/LOW 뱃지 |
| 생성일 | 언제 생성됐는지 |
| 액션 | 상세보기 버튼 |

**정렬**: 우선순위 → 품질 점수 → 생성일

### 3.4 콘텐츠 상세 페이지

```
┌─────────────────────────────────────────────────────────┐
│  [← 목록으로]                                [거절] [승인] │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────┐    제목: Kotlin 변수 완벽 정리         │
│  │              │    ────────────────────────           │
│  │  [비디오     │    설명: 변수의 종류와 사용법...        │
│  │   플레이어]  │                                        │
│  │              │    카테고리: [PROGRAMMING ▼]           │
│  │              │    난이도: [BEGINNER ▼]                │
│  └──────────────┘    태그: [Kotlin] [변수] [+추가]       │
│                                                         │
│  ─────────────────────────────────────────────────────  │
│  품질 점수: 85/100                                       │
│  우선순위: HIGH                                          │
│  ─────────────────────────────────────────────────────  │
│  원본 YouTube: "Kotlin Tutorial for Beginners"          │
│  채널: Developer Channel                                 │
│  YouTube ID: abc123xyz                                   │
└─────────────────────────────────────────────────────────┘
```

**기능:**
- 비디오 미리보기 (S3 Presigned URL)
- 제목/설명/카테고리/태그 수정 가능
- 승인: 수정된 내용으로 백엔드 contents 테이블에 INSERT
- 거절: 사유 입력 후 상태 변경

### 3.5 거절 모달

```
┌─────────────────────────────────────┐
│  콘텐츠 거절                    [X] │
├─────────────────────────────────────┤
│  거절 사유를 선택하세요:            │
│                                     │
│  ○ 품질 미달                        │
│  ○ 부적절한 내용                    │
│  ○ 저작권 우려                      │
│  ○ 카테고리 부적합                  │
│  ○ 기타                             │
│                                     │
│  상세 사유:                         │
│  ┌─────────────────────────────┐   │
│  │                             │   │
│  └─────────────────────────────┘   │
│                                     │
│              [취소]  [거절 확정]    │
└─────────────────────────────────────┘
```

---

## 4. 구현 단계

### Phase 1: 데이터베이스 및 엔티티 (1일)
1. `pending_contents` 테이블 생성
2. `backoffice_users` 테이블 생성
3. `PendingContent` 엔티티 및 Repository
4. `BackofficeUser` 엔티티 및 Repository
5. ReviewWriter 수정 → pending_contents INSERT

### Phase 2: Spring Security 설정 (0.5일)
1. SecurityConfig (폼 로그인)
2. UserDetailsService 구현
3. 로그인/로그아웃 페이지

### Phase 3: 백오피스 UI (1.5일)
1. Thymeleaf 레이아웃 (Bootstrap 5)
2. 대시보드 페이지
3. 승인 대기 목록 페이지
4. 콘텐츠 상세/수정 페이지
5. 승인/거절 처리

### Phase 4: 백엔드 연동 (1일)
1. 승인 시 백엔드 contents 테이블 INSERT
2. content_metadata 테이블 INSERT
3. S3 URL 처리 (Presigned URL 또는 CloudFront)
4. 트랜잭션 처리

### Phase 5: 테스트 및 마무리 (0.5일)
1. 통합 테스트
2. UI/UX 개선
3. 문서화

---

## 5. 파일 구조

```
upvy-ai-crawler/
├── src/main/kotlin/.../crawler/
│   ├── backoffice/
│   │   ├── config/
│   │   │   └── SecurityConfig.kt
│   │   ├── controller/
│   │   │   ├── DashboardController.kt
│   │   │   ├── PendingContentController.kt
│   │   │   └── LoginController.kt
│   │   ├── dto/
│   │   │   ├── PendingContentDto.kt
│   │   │   └── ApprovalRequest.kt
│   │   ├── service/
│   │   │   ├── PendingContentService.kt
│   │   │   ├── ContentPublishService.kt
│   │   │   └── BackofficeUserService.kt
│   │   └── repository/
│   │       ├── PendingContentRepository.kt
│   │       └── BackofficeUserRepository.kt
│   ├── domain/
│   │   ├── PendingContent.kt
│   │   ├── PendingContentStatus.kt
│   │   └── BackofficeUser.kt
│   └── ...
├── src/main/resources/
│   ├── templates/
│   │   ├── layout/
│   │   │   └── default.html
│   │   ├── login.html
│   │   ├── dashboard.html
│   │   ├── pending/
│   │   │   ├── list.html
│   │   │   └── detail.html
│   │   └── fragments/
│   │       ├── header.html
│   │       ├── sidebar.html
│   │       └── footer.html
│   ├── static/
│   │   ├── css/
│   │   │   └── backoffice.css
│   │   └── js/
│   │       └── backoffice.js
│   └── db/migration/
│       └── V2__create_pending_contents.sql
└── build.gradle.kts (Thymeleaf, Security 의존성 추가)
```

---

## 6. 백엔드 연동 상세

### 6.1 승인 시 INSERT되는 테이블

| 테이블 | 주요 컬럼 |
|--------|----------|
| `contents` | id(UUID), creator_id, content_type, url, thumbnail_url, status=PENDING |
| `content_metadata` | content_id, title, description, category, tags |

### 6.2 시스템 사용자 (AI 크롤러)

AI 크롤러가 생성한 콘텐츠의 `creator_id`로 사용할 시스템 계정:

```sql
-- 백엔드 users 테이블에 시스템 계정 추가
INSERT INTO users (id, email, nickname, oauth_provider, oauth_id, role)
VALUES (
    UUID_TO_BIN(UUID()),
    'system@upvy.ai',
    'Upvy AI',
    'SYSTEM',
    'AI_CRAWLER',
    'SYSTEM'
);
```

application.yml 설정:
```yaml
crawler:
  system-user-id: ${SYSTEM_USER_ID:00000000-0000-0000-0000-000000000001}
```

### 6.3 S3 URL 처리

pending_contents에는 S3 Key만 저장하고, 백오피스에서 표시할 때 Presigned URL 생성:

```kotlin
// PendingContentService.kt
fun getVideoPreviewUrl(s3Key: String): String {
    return s3Service.generatePresignedUrl(s3Key, Duration.ofHours(1))
}
```

---

## 7. API 엔드포인트

### 7.1 백오피스 페이지 (Thymeleaf)

| Method | URL | 설명 |
|--------|-----|------|
| GET | /backoffice/login | 로그인 페이지 |
| POST | /backoffice/login | 로그인 처리 |
| GET | /backoffice/dashboard | 대시보드 |
| GET | /backoffice/pending | 승인 대기 목록 |
| GET | /backoffice/pending/{id} | 콘텐츠 상세 |
| POST | /backoffice/pending/{id}/approve | 승인 처리 |
| POST | /backoffice/pending/{id}/reject | 거절 처리 |
| GET | /backoffice/approved | 승인된 목록 |
| GET | /backoffice/rejected | 거절된 목록 |

### 7.2 AJAX API (JSON)

| Method | URL | 설명 |
|--------|-----|------|
| GET | /api/backoffice/stats | 대시보드 통계 |
| PUT | /api/backoffice/pending/{id} | 메타데이터 수정 |
| GET | /api/backoffice/video-url/{id} | 비디오 Presigned URL |

---

## 8. 보안 고려사항

1. **인증**: Spring Security 폼 로그인
2. **비밀번호**: BCrypt 해시
3. **CSRF**: Thymeleaf 자동 처리
4. **세션**: 30분 타임아웃
5. **접근 제어**: `/backoffice/**` 경로 인증 필수

---

## 9. 향후 확장

1. **대량 승인**: 체크박스로 여러 콘텐츠 한 번에 승인
2. **자동 승인**: 품질 점수 90점 이상은 자동 승인
3. **알림**: 새 콘텐츠 생성 시 Slack/Email 알림
4. **통계 대시보드**: Grafana 연동
5. **A/B 테스트**: 승인된 콘텐츠의 성과 추적
