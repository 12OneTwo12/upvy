# Implementation Plan Addendum - 설계 변경사항

> 작성일: 2024-12-06
> 버전: 1.1
> 상태: 승인됨

---

## 변경사항 요약

| 항목 | 기존 | 변경 |
|------|------|------|
| YouTube 검색 | 고정 키워드 기반 | **AI 기반 지능형 검색** |
| 품질 점수 처리 | 70+ 자동 게시 | **모든 콘텐츠 사전 승인** (Option B) |
| 게시 계정 | 미정의 | **SYSTEM 계정 추가** |

---

## 1. AI 기반 지능형 YouTube 검색

### 1.1 개요

기존의 고정 키워드 검색 대신, LLM을 활용하여 사용자들이 원하는 양질의 콘텐츠를 지능적으로 검색합니다.

### 1.2 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                    AI-Powered Search Pipeline                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐             │
│  │  Context    │───▶│  LLM Query  │───▶│  YouTube    │             │
│  │  Collector  │    │  Generator  │    │  Search     │             │
│  └─────────────┘    └─────────────┘    └──────┬──────┘             │
│        │                                      │                     │
│        │ 트렌드, 시즌, 카테고리                 │                     │
│        │ 인기 콘텐츠 분석                      ▼                     │
│        │                            ┌─────────────┐                │
│        │                            │  LLM Video  │                │
│        │                            │  Evaluator  │                │
│        │                            └──────┬──────┘                │
│        │                                   │                       │
│        │                                   ▼ 메타데이터 기반 사전 평가   │
│        │                            ┌─────────────┐                │
│        └───────────────────────────▶│  Candidate  │                │
│                                     │  Selector   │                │
│                                     └─────────────┘                │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.3 구현 상세

#### 1.3.1 SearchContextCollector

현재 상황을 분석하여 검색 컨텍스트를 수집합니다.

```kotlin
/**
 * 검색 컨텍스트 수집기
 *
 * 현재 트렌드, 시즌, 인기 콘텐츠를 분석하여
 * LLM에게 제공할 컨텍스트를 생성합니다.
 */
interface SearchContextCollector {
    suspend fun collect(): SearchContext
}

data class SearchContext(
    val appCategories: List<String>,          // 앱 지원 카테고리
    val popularKeywords: List<String>,         // 최근 인기 검색어
    val topPerformingTags: List<String>,       // 인기 태그
    val seasonalContext: String?,              // 계절 정보 (봄/가드닝 시즌 등)
    val recentlyPublished: List<String>,       // 최근 게시된 콘텐츠 (중복 방지)
    val underrepresentedCategories: List<String> // 콘텐츠 부족 카테고리
)
```

#### 1.3.2 LlmSearchQueryGenerator

LLM을 활용하여 최적의 검색 쿼리를 생성합니다.

```kotlin
/**
 * LLM 기반 검색 쿼리 생성기
 */
interface SearchQueryGenerator {
    suspend fun generateQueries(context: SearchContext): List<SearchQuery>
}

data class SearchQuery(
    val query: String,                 // 실제 검색어
    val targetCategory: String,        // 목표 카테고리
    val expectedContentType: String,   // 예상 콘텐츠 유형
    val priority: Int                  // 우선순위 (1-10)
)
```

#### 1.3.3 검색 쿼리 생성 프롬프트

```kotlin
object SearchPromptTemplates {

    val GENERATE_SEARCH_QUERIES = """
        당신은 식물 재배 교육 플랫폼의 콘텐츠 큐레이터입니다.
        사용자들이 보고 싶어할 양질의 교육 콘텐츠를 찾기 위한
        YouTube 검색어를 생성해주세요.

        ## 플랫폼 정보
        - 앱 카테고리: {{appCategories}}
        - 최근 인기 키워드: {{popularKeywords}}
        - 현재 시즌: {{seasonalContext}}
        - 콘텐츠 부족 카테고리: {{underrepresentedCategories}}

        ## 최근 게시된 콘텐츠 (중복 방지)
        {{recentlyPublished}}

        ## 요구사항
        1. 교육적 가치가 높은 콘텐츠를 찾을 수 있는 검색어
        2. 초보자부터 전문가까지 다양한 난이도
        3. 영어/한국어 모두 포함
        4. 중복 콘텐츠 방지
        5. 계절성 고려

        ## 출력 형식 (JSON)
        {
            "queries": [
                {
                    "query": "검색어",
                    "targetCategory": "카테고리",
                    "expectedContentType": "tutorial|timelapse|tips|guide",
                    "priority": 1-10,
                    "reasoning": "이 검색어를 선택한 이유"
                }
            ]
        }

        10개의 검색어를 생성해주세요.
    """.trimIndent()
}
```

#### 1.3.4 LlmVideoEvaluator

YouTube 검색 결과의 메타데이터를 LLM이 사전 평가합니다.

```kotlin
/**
 * LLM 기반 비디오 사전 평가기
 *
 * 실제 다운로드 전에 메타데이터만으로 품질을 평가하여
 * API 쿼터와 처리 시간을 절약합니다.
 */
interface VideoEvaluator {
    suspend fun evaluate(candidates: List<VideoCandidate>): List<EvaluatedVideo>
}

data class EvaluatedVideo(
    val candidate: VideoCandidate,
    val relevanceScore: Int,          // 관련성 점수 (0-100)
    val educationalValue: Int,        // 교육적 가치 (0-100)
    val predictedQuality: Int,        // 예상 품질 (0-100)
    val recommendation: Recommendation,
    val reasoning: String
)

enum class Recommendation {
    HIGHLY_RECOMMENDED,   // 강력 추천 - 즉시 처리
    RECOMMENDED,          // 추천 - 처리 대기열에 추가
    MAYBE,                // 보류 - 다른 후보 없을 때 고려
    SKIP                  // 제외 - 처리하지 않음
}
```

#### 1.3.5 비디오 평가 프롬프트

```kotlin
val EVALUATE_VIDEO_CANDIDATES = """
    다음 YouTube 비디오 후보들을 평가해주세요.
    식물 재배 교육 플랫폼에 적합한 콘텐츠인지 판단합니다.

    ## 평가 기준
    1. **교육적 가치**: 시청자가 실제로 배울 수 있는 내용인가?
    2. **콘텐츠 품질**: 제목/설명이 전문적이고 신뢰할 수 있는가?
    3. **적합성**: 쇼트폼으로 편집하기 좋은 구조인가?
    4. **참여도 예상**: 조회수, 채널 신뢰도 등

    ## 비디오 후보 목록
    {{candidates}}

    ## 출력 형식 (JSON)
    {
        "evaluations": [
            {
                "videoId": "...",
                "relevanceScore": 0-100,
                "educationalValue": 0-100,
                "predictedQuality": 0-100,
                "recommendation": "HIGHLY_RECOMMENDED|RECOMMENDED|MAYBE|SKIP",
                "reasoning": "평가 근거"
            }
        ]
    }
""".trimIndent()
```

### 1.4 검색 파이프라인 플로우

```
1. SearchContextCollector.collect()
   └── DB에서 현재 상태 분석
   └── 인기 콘텐츠, 트렌드, 시즌 정보 수집

2. SearchQueryGenerator.generateQueries(context)
   └── LLM이 10개 검색 쿼리 생성
   └── 우선순위별 정렬

3. YouTubeClient.search(queries)
   └── 각 쿼리로 YouTube API 검색
   └── CC 라이선스 필터링
   └── 중복 제거

4. VideoEvaluator.evaluate(candidates)
   └── LLM이 메타데이터 기반 사전 평가
   └── HIGHLY_RECOMMENDED, RECOMMENDED만 선택

5. AiContentJob 생성
   └── 선택된 비디오를 처리 대기열에 추가
```

---

## 2. 품질 점수 처리 - Option B (모든 콘텐츠 사전 승인)

### 2.1 변경된 플로우

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Quality Review Flow                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  품질 점수 산정                                                       │
│       │                                                             │
│       ▼                                                             │
│  ┌─────────────────────────────────────────────────────┐            │
│  │                    Score >= 85                       │            │
│  │              PENDING_APPROVAL (우선)                 │───────┐    │
│  │                  priority: HIGH                      │       │    │
│  └─────────────────────────────────────────────────────┘       │    │
│                                                                │    │
│  ┌─────────────────────────────────────────────────────┐       │    │
│  │                  70 <= Score < 85                    │       │    │
│  │              PENDING_APPROVAL (일반)                 │───────┤    │
│  │                  priority: NORMAL                    │       │    │
│  └─────────────────────────────────────────────────────┘       │    │
│                                                                │    │
│  ┌─────────────────────────────────────────────────────┐       │    │
│  │                    Score < 70                        │       │    │
│  │                    REJECTED                          │       │    │
│  │              (자동 거절, 검토 불필요)                  │       │    │
│  └─────────────────────────────────────────────────────┘       │    │
│                                                                │    │
│                          ┌─────────────────────┐               │    │
│                          │   관리자 검토 대기열  │◀──────────────┘    │
│                          │   (우선순위별 정렬)   │                    │
│                          └──────────┬──────────┘                    │
│                                     │                               │
│                    ┌────────────────┼────────────────┐              │
│                    ▼                ▼                ▼              │
│               APPROVED          REJECTED         NEEDS_EDIT         │
│                  │                                   │              │
│                  ▼                                   ▼              │
│              PUBLISHED                         재편집 후 재심사       │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 JobStatus 업데이트

```kotlin
enum class JobStatus {
    // 처리 단계
    PENDING,              // 생성됨, 처리 대기
    CRAWLED,              // 비디오 다운로드 완료
    TRANSCRIBED,          // 음성-텍스트 변환 완료
    ANALYZED,             // LLM 분석 완료
    EDITED,               // 비디오 편집 완료

    // 검토 단계
    PENDING_APPROVAL,     // 관리자 승인 대기 (NEW)

    // 최종 상태
    APPROVED,             // 승인됨 (게시 준비 완료)
    PUBLISHED,            // 게시 완료
    REJECTED,             // 거절됨
    NEEDS_EDIT,           // 재편집 필요 (NEW)

    // 에러
    FAILED                // 처리 실패
}

enum class ReviewPriority {
    HIGH,     // 85점 이상 - 우선 검토
    NORMAL,   // 70-84점 - 일반 검토
    LOW       // 수동 요청 등
}
```

### 2.3 AiContentJob 엔티티 추가 필드

```kotlin
@Entity
@Table(name = "ai_content_job")
class AiContentJob(
    // ... 기존 필드 ...

    // 검토 관련 필드 추가
    @Column(name = "review_priority")
    @Enumerated(EnumType.STRING)
    var reviewPriority: ReviewPriority? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: LocalDateTime? = null,

    @Column(name = "reviewed_by")
    var reviewedBy: String? = null,

    @Column(name = "review_comment")
    var reviewComment: String? = null
)
```

### 2.4 검토 API (Backend에 추가 필요)

```kotlin
// 백엔드에 추가할 관리자 API

/**
 * 승인 대기 콘텐츠 목록 조회
 */
@GetMapping("/admin/ai-content/pending")
suspend fun getPendingContent(
    @RequestParam priority: ReviewPriority? = null
): List<AiContentReviewDto>

/**
 * 콘텐츠 승인
 */
@PostMapping("/admin/ai-content/{jobId}/approve")
suspend fun approveContent(
    @PathVariable jobId: Long,
    @RequestBody request: ApproveRequest
): AiContentReviewDto

/**
 * 콘텐츠 거절
 */
@PostMapping("/admin/ai-content/{jobId}/reject")
suspend fun rejectContent(
    @PathVariable jobId: Long,
    @RequestBody request: RejectRequest
): AiContentReviewDto
```

---

## 3. AI 크롤러용 시스템 계정

### 3.1 문제점

현재 User 모델은 OAuth 로그인 기반으로 설계되어 있어 시스템 계정을 생성할 수 없습니다.

```kotlin
// 현재 User 모델
data class User(
    val provider: OAuthProvider,  // GOOGLE, NAVER, KAKAO
    val providerId: String,       // OAuth 제공자의 사용자 ID
    // ...
)
```

### 3.2 해결 방안

**OAuthProvider에 SYSTEM 추가**

```kotlin
enum class OAuthProvider {
    GOOGLE,
    NAVER,
    KAKAO,
    SYSTEM    // NEW: 시스템 계정용
}
```

### 3.3 백엔드 변경사항

#### 3.3.1 OAuthProvider 수정

```kotlin
// User.kt
enum class OAuthProvider {
    GOOGLE,
    NAVER,
    KAKAO,
    SYSTEM    // 시스템 계정 (AI 크롤러 등)
}
```

#### 3.3.2 시스템 계정 생성 SQL

```sql
-- 시스템 계정 생성 (1회성 마이그레이션)
-- ID는 고정값 사용하여 크롤러에서 설정으로 참조 가능

INSERT INTO users (id, email, provider, provider_id, role, status, created_at, created_by)
VALUES (
    '00000000-0000-0000-0000-000000000001',  -- 고정 UUID
    'ai-crawler@growsnap.app',
    'SYSTEM',
    'ai-content-crawler',
    'USER',  -- 또는 새로운 역할 SYSTEM
    'ACTIVE',
    NOW(),
    'system'
);

-- 시스템 계정 프로필 생성
INSERT INTO user_profiles (user_id, nickname, profile_image_url, bio, created_at, created_by)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'GrowSnap AI',
    'https://cdn.growsnap.app/system/ai-avatar.png',
    'AI가 큐레이션한 식물 재배 콘텐츠를 제공합니다.',
    NOW(),
    'system'
);
```

#### 3.3.3 크롤러 설정

```yaml
# grow-snap-ai-crawler/application.yml

crawler:
  # 콘텐츠 게시에 사용할 시스템 계정 ID
  system-user-id: "00000000-0000-0000-0000-000000000001"
  system-user-name: "GrowSnap AI"
```

### 3.4 콘텐츠 게시 시 사용

```kotlin
@Service
class PublishService(
    @Value("\${crawler.system-user-id}")
    private val systemUserId: String,
    private val contentRepository: ContentRepository
) {
    fun publishContent(job: AiContentJob, segment: AiContentSegment) {
        val content = Content(
            creatorId = UUID.fromString(systemUserId),
            contentType = ContentType.VIDEO,
            url = segment.s3Key!!,
            thumbnailUrl = segment.thumbnailS3Key!!,
            // ...
        )
        contentRepository.save(content)
    }
}
```

---

## 4. 구현 순서 업데이트

### Phase 0: 백엔드 사전 작업 (NEW)

| ID | 작업 | 파일 | 우선순위 |
|----|------|------|----------|
| P0-01 | OAuthProvider에 SYSTEM 추가 | `User.kt` | CRITICAL |
| P0-02 | 시스템 계정 생성 SQL | `create-table-sql.sql` | CRITICAL |
| P0-03 | 관리자 검토 API 추가 | `AdminController.kt` | HIGH |

### Phase 1: YouTube 크롤링 (수정)

| ID | 작업 | 파일 | 변경사항 |
|----|------|------|----------|
| P1-01 | SearchContextCollector 구현 | `search/SearchContextCollector.kt` | NEW |
| P1-02 | LlmSearchQueryGenerator 구현 | `search/LlmSearchQueryGenerator.kt` | NEW |
| P1-03 | LlmVideoEvaluator 구현 | `search/LlmVideoEvaluator.kt` | NEW |
| P1-04 | YouTube 클라이언트 | `client/youtube/YouTubeClient.kt` | 기존 |

### Phase 5: 품질 검토 (수정)

| ID | 작업 | 변경사항 |
|----|------|----------|
| P5-01 | ReviewPriority 추가 | HIGH/NORMAL 우선순위 구분 |
| P5-02 | 모든 콘텐츠 PENDING_APPROVAL | 자동 게시 제거 |
| P5-03 | PublishService | 승인된 콘텐츠만 게시 |

---

## 5. 데이터베이스 스키마 추가

```sql
-- ai_content_job 테이블 추가 컬럼
ALTER TABLE ai_content_job
ADD COLUMN review_priority VARCHAR(20) NULL AFTER quality_score,
ADD COLUMN reviewed_at DATETIME NULL AFTER review_priority,
ADD COLUMN reviewed_by VARCHAR(100) NULL AFTER reviewed_at,
ADD COLUMN review_comment TEXT NULL AFTER reviewed_by;

CREATE INDEX idx_review_priority ON ai_content_job(review_priority);
CREATE INDEX idx_status_priority ON ai_content_job(status, review_priority);
```

---

## 6. 체크리스트

### 백엔드 작업
- [ ] OAuthProvider에 SYSTEM 추가
- [ ] 시스템 계정 생성 SQL 실행
- [ ] 관리자 검토 API 구현

### 크롤러 작업
- [ ] SearchContextCollector 구현
- [ ] LlmSearchQueryGenerator 구현
- [ ] LlmVideoEvaluator 구현
- [ ] ReviewPriority 로직 구현
- [ ] PublishService에서 systemUserId 사용

---

> 이 문서는 IMPLEMENTATION_PLAN.md의 보충 문서입니다.
