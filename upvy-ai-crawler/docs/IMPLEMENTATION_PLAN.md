# AI Content Crawler - 구현 계획서

> 작성일: 2024-12-06
> 버전: 1.1
> 관련 이슈: [#14 AI 콘텐츠 생성 시스템 구현](https://github.com/12OneTwo12/upvy/issues/14)

---

## 1. Executive Summary

### 1.1 목적
YouTube CC 라이선스 콘텐츠를 자동으로 수집, 분석, 편집하여 교육용 쇼트폼 콘텐츠를 생성하는 배치 시스템 구현

### 1.2 핵심 목표
- **자동화**: AI 기반 지능형 검색 및 콘텐츠 생성
- **품질 보장**: AI 품질 점수 + 관리자 사전 승인 (Option B)
- **확장성**: AI 제공자(Vertex AI, OpenAI 등) 유연한 교체 가능
- **안정성**: 실패 복구, 재시도 로직, 배치 모니터링

### 1.3 기술 스택
| 구분 | 기술 |
|------|------|
| Framework | Spring Boot 3.x + Spring Batch 5.x |
| Language | Kotlin 1.9.x |
| AI/ML | Vertex AI (기본), OpenAI (대체) |
| STT | Whisper API |
| Video Processing | FFmpeg, yt-dlp |
| Storage | AWS S3 |
| Database | MySQL 8.0 (기존 백엔드 DB 공유) |
| Caching | Redis |

---

## 2. Architecture Overview

### 2.1 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                         AI Content Crawler                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐             │
│  │  Scheduler  │───▶│   Job       │───▶│   Step 1    │             │
│  │  (Cron)     │    │  Launcher   │    │  Crawl      │             │
│  └─────────────┘    └─────────────┘    └──────┬──────┘             │
│                                               │                     │
│                                               ▼                     │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐             │
│  │  Step 5     │◀───│   Step 4    │◀───│   Step 2    │             │
│  │  Review     │    │   Edit      │    │  Transcribe │             │
│  └──────┬──────┘    └─────────────┘    └──────┬──────┘             │
│         │                                     │                     │
│         │           ┌─────────────┐           │                     │
│         │           │   Step 3    │◀──────────┘                     │
│         │           │   Analyze   │                                 │
│         │           └─────────────┘                                 │
│         ▼                                                           │
│  ┌─────────────┐                                                   │
│  │  Publish    │───▶ upvy-backend DB (관리자 승인 후)          │
│  └─────────────┘                                                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

External Services:
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ YouTube API  │  │  Vertex AI   │  │  Whisper API │  │   AWS S3     │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
```

### 2.2 배치 Job 구성

```
aiContentJob
├── Step 1: crawlStep (AI 기반 YouTube 검색)
│   ├── ItemReader: AiPoweredSearchReader
│   ├── ItemProcessor: VideoCandidateProcessor
│   └── ItemWriter: AiContentJobWriter
│
├── Step 2: transcribeStep (음성→텍스트 변환)
│   ├── ItemReader: PendingJobReader
│   ├── ItemProcessor: TranscribeProcessor
│   └── ItemWriter: TranscriptWriter
│
├── Step 3: analyzeStep (LLM 분석 및 세그먼트 추출)
│   ├── ItemReader: TranscribedJobReader
│   ├── ItemProcessor: LlmAnalyzeProcessor
│   └── ItemWriter: AnalysisWriter
│
├── Step 4: editStep (FFmpeg 편집)
│   ├── ItemReader: AnalyzedJobReader
│   ├── ItemProcessor: VideoEditProcessor
│   └── ItemWriter: EditedVideoWriter
│
└── Step 5: reviewStep (품질 검토 → 승인 대기)
    ├── ItemReader: EditedJobReader
    ├── ItemProcessor: QualityReviewProcessor
    └── ItemWriter: PendingApprovalWriter
```

### 2.3 AI 추상화 레이어

```kotlin
// LLM 제공자 추상화
interface LlmClient {
    suspend fun analyze(prompt: String): String
    suspend fun extractKeySegments(transcript: String): List<Segment>
    suspend fun generateMetadata(content: String): ContentMetadata
    suspend fun generateSearchQueries(context: SearchContext): List<SearchQuery>  // NEW
    suspend fun evaluateVideos(candidates: List<VideoCandidate>): List<EvaluatedVideo>  // NEW
}

// STT 제공자 추상화
interface SttClient {
    suspend fun transcribe(audioUrl: String): TranscriptResult
}

// 전략 패턴으로 구현체 선택
// application.yml의 ai.provider 설정에 따라 자동 선택
```

---

## 3. 백엔드 선행 작업 (Phase 0)

> 크롤러 구현 전 백엔드에서 먼저 처리해야 할 작업

### 3.1 시스템 계정 생성

크롤러가 콘텐츠를 게시할 때 사용할 전용 계정이 필요합니다.

#### 3.1.1 OAuthProvider 수정

```kotlin
// User.kt
enum class OAuthProvider {
    GOOGLE,
    NAVER,
    KAKAO,
    SYSTEM    // NEW: 시스템 계정용
}
```

#### 3.1.2 시스템 계정 생성 SQL

```sql
-- 시스템 계정 생성 (1회성)
INSERT INTO users (id, email, provider, provider_id, role, status, created_at, created_by)
VALUES (
    '00000000-0000-0000-0000-000000000001',  -- 고정 UUID
    'ai-crawler@upvy.app',
    'SYSTEM',
    'ai-content-crawler',
    'USER',
    'ACTIVE',
    NOW(),
    'system'
);

-- 시스템 계정 프로필 생성
INSERT INTO user_profiles (user_id, nickname, profile_image_url, bio, created_at, created_by)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Upvy AI',
    'https://upvy-images.s3.amazonaws.com/profile-images/8a8ea321-9fe3-4e52-af4e-aa6711a87ef2/profile_8a8ea321-9fe3-4e52-af4e-aa6711a87ef2_1764745438868.jpg',
    'AI가 큐레이션한 교육 숏폼 콘텐츠를 제공합니다.',
    NOW(),
    'system'
);
```

### 3.2 작업 목록

| ID | 작업 | 파일 | 우선순위 |
|----|------|------|----------|
| P0-01 | OAuthProvider에 SYSTEM 추가 | `domain/user/model/User.kt` | CRITICAL |
| P0-02 | 시스템 계정 생성 SQL 실행 | DB Migration | CRITICAL |
| P0-03 | 관리자 검토 API 추가 (선택) | `AdminController.kt` | MEDIUM |

---

## 4. Implementation Phases

### Phase 1: AI 기반 YouTube 크롤링 (Step 1)

> 기존 고정 키워드 검색 → **LLM 기반 지능형 검색**으로 변경

#### 4.1.1 AI 검색 파이프라인

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

#### 4.1.2 SearchContextCollector

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
    val seasonalContext: String?,              // 계절 정보
    val recentlyPublished: List<String>,       // 최근 게시된 콘텐츠 (중복 방지)
    val underrepresentedCategories: List<String> // 콘텐츠 부족 카테고리
)
```

#### 4.1.3 LLM 검색 쿼리 생성

```kotlin
data class SearchQuery(
    val query: String,                 // 실제 검색어
    val targetCategory: String,        // 목표 카테고리
    val expectedContentType: String,   // 예상 콘텐츠 유형
    val priority: Int                  // 우선순위 (1-10)
)

// 프롬프트 템플릿
val GENERATE_SEARCH_QUERIES = """
    당신은 Upvy의 콘텐츠 큐레이터입니다.

    ## Upvy 소개
    "스크롤 시간을 성장 시간으로" - TikTok/Reels처럼 재미있지만,
    스크롤하다 보면 자연스럽게 새로운 것을 배우게 되는 교육 숏폼 플랫폼입니다.
    딱딱한 강의가 아닌, 재미있고 흥미로운 인사이트를 제공합니다.

    ## 타겟 사용자
    - 출퇴근/쉬는 시간에 부담없이 뭔가 배우고 싶은 사람들
    - "또 시간 낭비했다" 대신 "오, 이거 몰랐는데!" 경험을 원하는 사람들

    ## 콘텐츠 카테고리
    학문(언어, 과학, 역사, 수학, 예술), 비즈니스(스타트업, 마케팅, 프로그래밍, 디자인),
    자기계발(생산성, 심리학, 재테크, 건강), 라이프스타일(육아, 요리, 여행, 취미), 트렌드

    ## 현재 상황
    - 인기 키워드: {{popularKeywords}}
    - 콘텐츠 부족 카테고리: {{underrepresentedCategories}}
    - 시즌/트렌드: {{seasonalContext}}

    ## 요청
    위 정보를 바탕으로 YouTube에서 양질의 교육 콘텐츠를 찾기 위한
    검색어 10개를 JSON 형식으로 생성해주세요.

    숏폼으로 편집하기 좋고, 시청자가 "오, 몰랐는데!" 할 만한 콘텐츠를 찾아주세요.
""".trimIndent()
```

#### 4.1.4 LLM 비디오 사전 평가

```kotlin
/**
 * LLM 기반 비디오 사전 평가기
 *
 * 실제 다운로드 전에 메타데이터만으로 품질을 평가하여
 * API 쿼터와 처리 시간을 절약합니다.
 */
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

#### 4.1.5 작업 목록

| ID | 작업 | 파일 | 우선순위 |
|----|------|------|----------|
| P1-01 | SearchContextCollector 구현 | `search/SearchContextCollector.kt` | HIGH |
| P1-02 | LlmSearchQueryGenerator 구현 | `search/LlmSearchQueryGenerator.kt` | HIGH |
| P1-03 | LlmVideoEvaluator 구현 | `search/LlmVideoEvaluator.kt` | HIGH |
| P1-04 | YouTube Data API v3 클라이언트 | `client/youtube/YouTubeClient.kt` | HIGH |
| P1-05 | CrawlStep Reader/Processor/Writer | `batch/step/crawl/*.kt` | HIGH |
| P1-06 | yt-dlp 래퍼 구현 | `client/video/YtDlpWrapper.kt` | MEDIUM |
| P1-07 | 단위/통합 테스트 | `test/.../crawl/*Test.kt` | HIGH |

#### 4.1.6 API 쿼터 관리

```yaml
# YouTube Data API 쿼터: 10,000 units/day
# - search.list: 100 units
# - videos.list: 1 unit

# 일일 최대 검색 횟수: ~100회
# 전략:
#   - 배치당 검색 10회로 제한
#   - 검색 결과 캐싱 (Redis, TTL 24h)
#   - LLM 사전 평가로 불필요한 처리 최소화
```

---

### Phase 2: 음성-텍스트 변환 (Step 2)

#### 4.2.1 작업 목록

| ID | 작업 | 파일 | 우선순위 |
|----|------|------|----------|
| P2-01 | SttClient 인터페이스 정의 | `client/SttClient.kt` | HIGH |
| P2-02 | WhisperSttClient 구현 | `client/stt/WhisperSttClient.kt` | HIGH |
| P2-03 | VertexAiSttClient 구현 | `client/stt/VertexAiSttClient.kt` | MEDIUM |
| P2-04 | 오디오 추출 서비스 | `service/AudioExtractService.kt` | HIGH |
| P2-05 | TranscribeStep Reader/Processor/Writer | `batch/step/transcribe/*.kt` | HIGH |
| P2-06 | 단위/통합 테스트 | `test/.../transcribe/*Test.kt` | HIGH |

#### 4.2.2 Whisper API 통합

```kotlin
data class TranscriptResult(
    val text: String,                 // 전체 텍스트
    val segments: List<TranscriptSegment>,
    val language: String?,
    val confidence: Float?
)

data class TranscriptSegment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)
```

#### 4.2.3 오디오 처리 파이프라인

```
1. 비디오 파일 → FFmpeg → 오디오 추출 (MP3/WAV)
2. 오디오 파일 → S3 업로드 (임시 저장)
3. S3 URL → Whisper API → TranscriptResult
4. 결과 저장 → DB 업데이트
5. 임시 오디오 파일 → 정리 (cleanup)
```

---

### Phase 3: LLM 분석 및 세그먼트 추출 (Step 3)

#### 4.3.1 작업 목록

| ID | 작업 | 파일 | 우선순위 |
|----|------|------|----------|
| P3-01 | LlmClient 인터페이스 정의 | `client/LlmClient.kt` | HIGH |
| P3-02 | VertexAiLlmClient 구현 | `client/llm/VertexAiLlmClient.kt` | HIGH |
| P3-03 | OpenAiLlmClient 구현 | `client/llm/OpenAiLlmClient.kt` | MEDIUM |
| P3-04 | 프롬프트 템플릿 관리 | `prompt/PromptTemplates.kt` | HIGH |
| P3-05 | AnalyzeStep Reader/Processor/Writer | `batch/step/analyze/*.kt` | HIGH |
| P3-06 | 단위/통합 테스트 | `test/.../analyze/*Test.kt` | HIGH |

#### 4.3.2 LLM 프롬프트 설계

```kotlin
object PromptTemplates {

    val SEGMENT_EXTRACTION = """
        다음은 교육용 영상의 자막 내용입니다.
        학습 가치가 높은 핵심 구간을 3-5개 추출해주세요.

        각 구간에 대해 다음 정보를 JSON 형식으로 제공해주세요:
        - startTimeMs: 시작 시간 (밀리초)
        - endTimeMs: 종료 시간 (밀리초)
        - title: 구간 제목 (15자 이내)
        - description: 구간 설명 (50자 이내)
        - keywords: 관련 키워드 (3개)
        - qualityScore: 학습 가치 점수 (1-100)

        자막 내용:
        {{transcript}}
    """.trimIndent()

    val METADATA_GENERATION = """
        다음 영상 정보를 바탕으로 SNS 쇼트폼 콘텐츠 메타데이터를 생성해주세요.

        원본 제목: {{originalTitle}}
        원본 설명: {{originalDescription}}
        추출된 구간: {{segment}}

        다음 형식으로 응답해주세요:
        - title: 매력적인 제목 (30자 이내)
        - description: 설명 (100자 이내)
        - tags: 해시태그 (5개)
        - category: 카테고리
        - difficulty: 난이도 (BEGINNER/INTERMEDIATE/ADVANCED)
    """.trimIndent()
}
```

---

### Phase 4: 비디오 편집 (Step 4)

#### 4.4.1 작업 목록

| ID | 작업 | 파일 | 우선순위 |
|----|------|------|----------|
| P4-01 | FFmpegWrapper 구현 | `client/video/FFmpegWrapper.kt` | HIGH |
| P4-02 | 비디오 클리핑 서비스 | `service/VideoClipService.kt` | HIGH |
| P4-03 | 썸네일 생성 서비스 | `service/ThumbnailService.kt` | MEDIUM |
| P4-04 | S3 업로드 서비스 | `service/S3UploadService.kt` | HIGH |
| P4-05 | EditStep Reader/Processor/Writer | `batch/step/edit/*.kt` | HIGH |
| P4-06 | 단위/통합 테스트 | `test/.../edit/*Test.kt` | HIGH |

#### 4.4.2 FFmpeg 명령어 템플릿

```kotlin
object FFmpegCommands {

    // 구간 클리핑
    fun clip(input: String, output: String, startMs: Long, endMs: Long): List<String>

    // 썸네일 추출
    fun thumbnail(input: String, output: String, timeMs: Long): List<String>

    // 세로 리사이징 (9:16 쇼츠 포맷)
    fun resizeVertical(input: String, output: String): List<String>
}
```

#### 4.4.3 S3 구조

```
s3://upvy-ai-content/
├── raw/                      # 원본 다운로드
│   └── {videoId}/video.mp4
├── clips/                    # 편집된 클립
│   └── {jobId}/{segmentId}.mp4
├── thumbnails/               # 썸네일
│   └── {jobId}/{segmentId}.jpg
└── temp/                     # 임시 파일 (자동 정리)
    └── audio/{jobId}.mp3
```

---

### Phase 5: 품질 검토 및 승인 대기 (Step 5)

> **Option B 적용**: 모든 콘텐츠는 관리자 사전 승인 필요

#### 4.5.1 품질 점수 처리 플로우

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Quality Review Flow (Option B)                  │
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

#### 4.5.2 JobStatus 및 ReviewPriority

```kotlin
enum class JobStatus {
    // 처리 단계
    PENDING,              // 생성됨, 처리 대기
    CRAWLED,              // 비디오 다운로드 완료
    TRANSCRIBED,          // 음성-텍스트 변환 완료
    ANALYZED,             // LLM 분석 완료
    EDITED,               // 비디오 편집 완료

    // 검토 단계
    PENDING_APPROVAL,     // 관리자 승인 대기

    // 최종 상태
    APPROVED,             // 승인됨 (게시 준비 완료)
    PUBLISHED,            // 게시 완료
    REJECTED,             // 거절됨
    NEEDS_EDIT,           // 재편집 필요

    // 에러
    FAILED                // 처리 실패
}

enum class ReviewPriority {
    HIGH,     // 85점 이상 - 우선 검토
    NORMAL,   // 70-84점 - 일반 검토
    LOW       // 기타
}
```

#### 4.5.3 품질 점수 산정 기준

```kotlin
data class QualityScore(
    val totalScore: Int,              // 종합 점수 (0-100)
    val contentRelevance: Int,        // 콘텐츠 관련성 (0-25)
    val audioClarity: Int,            // 오디오 명확성 (0-25)
    val visualQuality: Int,           // 영상 품질 (0-25)
    val educationalValue: Int         // 교육적 가치 (0-25)
)

// 처리 기준 (Option B)
// - 85점 이상: PENDING_APPROVAL (priority: HIGH) - 우선 검토
// - 70-84점: PENDING_APPROVAL (priority: NORMAL) - 일반 검토
// - 70점 미만: REJECTED - 자동 거절
```

#### 4.5.4 작업 목록

| ID | 작업 | 파일 | 우선순위 |
|----|------|------|----------|
| P5-01 | 품질 점수 계산 서비스 | `service/QualityScoreService.kt` | HIGH |
| P5-02 | ReviewStep Reader/Processor/Writer | `batch/step/review/*.kt` | HIGH |
| P5-03 | PublishService (승인 후 게시) | `service/PublishService.kt` | HIGH |
| P5-04 | 단위/통합 테스트 | `test/.../review/*Test.kt` | HIGH |

#### 4.5.5 크롤러 설정

```yaml
# upvy-ai-crawler/application.yml

crawler:
  # 콘텐츠 게시에 사용할 시스템 계정 ID
  system-user-id: "00000000-0000-0000-0000-000000000001"
  system-user-name: "Upvy AI"

  # 품질 점수 기준
  quality:
    high-priority-threshold: 85   # 우선 검토 기준
    approval-threshold: 70        # 승인 대기 기준 (미만은 자동 거절)
```

#### 4.5.6 콘텐츠 게시 시 백엔드 테이블 INSERT

> 관리자 승인 후 콘텐츠를 게시할 때 백엔드 DB에 INSERT해야 하는 테이블들

```
PublishService.publish(job, segment)
    │
    ├── 1. contents 테이블 INSERT
    │       - id: UUID (새로 생성)
    │       - creator_id: 시스템 계정 UUID
    │       - content_type: 'VIDEO'
    │       - url: S3 URL (편집된 클립)
    │       - thumbnail_url: S3 URL (썸네일)
    │       - duration: 영상 길이 (초)
    │       - width, height: 1080x1920 (9:16)
    │       - status: 'PUBLISHED'
    │
    ├── 2. content_metadata 테이블 INSERT
    │       - content_id: contents.id 참조
    │       - title: AI가 생성한 제목
    │       - description: AI가 생성한 설명
    │       - category: 카테고리 (Category enum)
    │       - tags: JSON ["태그1", "태그2", ...]
    │       - language: 'ko' (기본값)
    │
    ├── 3. content_interactions 테이블 INSERT
    │       - content_id: contents.id 참조
    │       - like_count: 0
    │       - comment_count: 0
    │       - save_count: 0
    │       - share_count: 0
    │       - view_count: 0
    │
    └── 4. ai_content_job 상태 업데이트
            - status: 'PUBLISHED'
            - published_content_id: contents.id 참조 (추적용)
```

**PublishService 구현 예시:**

```kotlin
@Service
class PublishService(
    private val contentRepository: ContentRepository,
    private val contentMetadataRepository: ContentMetadataRepository,
    private val contentInteractionRepository: ContentInteractionRepository,
    private val aiContentJobRepository: AiContentJobRepository,
    @Value("\${crawler.system-user-id}") private val systemUserId: String
) {
    @Transactional
    fun publish(job: AiContentJob, segment: AiContentSegment): UUID {
        val contentId = UUID.randomUUID()
        val creatorId = UUID.fromString(systemUserId)
        val now = Instant.now()

        // 1. contents 테이블 INSERT
        val content = Content(
            id = contentId,
            creatorId = creatorId,
            contentType = ContentType.VIDEO,
            url = segment.s3Key!!,
            thumbnailUrl = segment.thumbnailS3Key!!,
            duration = ((segment.endTimeMs - segment.startTimeMs) / 1000).toInt(),
            width = 1080,
            height = 1920,
            status = ContentStatus.PUBLISHED,
            createdAt = now,
            createdBy = systemUserId,
            updatedAt = now,
            updatedBy = systemUserId
        )
        contentRepository.save(content)

        // 2. content_metadata 테이블 INSERT
        val metadata = ContentMetadata(
            contentId = contentId,
            title = segment.title ?: job.originalTitle ?: "Untitled",
            description = segment.description,
            category = Category.valueOf(job.category ?: "OTHER"),
            tags = segment.keywords,
            language = "ko",
            createdAt = now,
            createdBy = systemUserId,
            updatedAt = now,
            updatedBy = systemUserId
        )
        contentMetadataRepository.save(metadata)

        // 3. content_interactions 테이블 INSERT (초기값 0)
        val interaction = ContentInteraction(
            contentId = contentId,
            likeCount = 0,
            commentCount = 0,
            saveCount = 0,
            shareCount = 0,
            viewCount = 0,
            createdAt = now,
            createdBy = systemUserId,
            updatedAt = now,
            updatedBy = systemUserId
        )
        contentInteractionRepository.save(interaction)

        // 4. ai_content_job 상태 업데이트
        job.status = JobStatus.PUBLISHED
        job.publishedContentId = contentId.toString()
        job.updatedAt = now
        aiContentJobRepository.save(job)

        return contentId
    }
}
```

---

## 5. Database Schema

### 5.1 테이블 설계

```sql
-- AI 콘텐츠 작업 테이블
CREATE TABLE ai_content_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    youtube_video_id VARCHAR(50) NOT NULL,
    youtube_channel_id VARCHAR(100),
    original_title VARCHAR(500),
    original_description TEXT,
    video_url VARCHAR(1000),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    transcript_text TEXT,
    raw_video_s3_key VARCHAR(500),
    quality_score INT,
    review_priority VARCHAR(20),      -- HIGH, NORMAL, LOW
    reviewed_at DATETIME,
    reviewed_by VARCHAR(100),
    review_comment TEXT,
    error_message TEXT,
    retry_count INT DEFAULT 0,

    -- Audit Trail
    created_at DATETIME NOT NULL,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    deleted_at DATETIME,

    INDEX idx_status (status),
    INDEX idx_review_priority (review_priority),
    INDEX idx_status_priority (status, review_priority),
    INDEX idx_youtube_video_id (youtube_video_id),
    INDEX idx_created_at (created_at)
);

-- AI 콘텐츠 세그먼트 테이블
CREATE TABLE ai_content_segment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    start_time_ms BIGINT NOT NULL,
    end_time_ms BIGINT NOT NULL,
    title VARCHAR(200),
    description TEXT,
    keywords JSON,
    s3_key VARCHAR(500),
    thumbnail_s3_key VARCHAR(500),
    quality_score INT,
    is_selected BOOLEAN DEFAULT FALSE,

    -- Audit Trail
    created_at DATETIME NOT NULL,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    deleted_at DATETIME,

    FOREIGN KEY (job_id) REFERENCES ai_content_job(id),
    INDEX idx_job_id (job_id),
    INDEX idx_is_selected (is_selected)
);
```

### 5.2 상태 전이 다이어그램

```
PENDING → CRAWLED → TRANSCRIBED → ANALYZED → EDITED → PENDING_APPROVAL
   ↓          ↓           ↓            ↓          ↓              ↓
FAILED    FAILED     FAILED      FAILED    FAILED       ┌────────┴────────┐
                                                        ▼                 ▼
                                                   APPROVED           REJECTED
                                                      ↓
                                                  PUBLISHED
```

---

## 6. Testing Strategy

### 6.1 테스트 피라미드

```
                    ┌───────────────────┐
                    │   E2E Tests (5%)   │  ← 전체 파이프라인 테스트
                    ├───────────────────┤
                    │ Integration (25%)  │  ← Step 통합 테스트
                    ├───────────────────┤
                    │   Unit Tests (70%) │  ← 개별 컴포넌트 테스트
                    └───────────────────┘
```

### 6.2 테스트 범위

| 영역 | 커버리지 목표 | 필수 테스트 |
|------|--------------|------------|
| Client 레이어 | 80%+ | API 호출, 에러 핸들링 |
| Service 레이어 | 90%+ | 비즈니스 로직 |
| Batch Step | 85%+ | Reader/Processor/Writer |
| Domain | 95%+ | 엔티티, 값 객체 |

### 6.3 Mock 전략

```kotlin
// AI 클라이언트 Mock
class MockLlmClient : LlmClient {
    override suspend fun generateSearchQueries(context: SearchContext): List<SearchQuery> {
        return listOf(SearchQuery("productivity tips", "PRODUCTIVITY", "tips", 10))
    }
}

// External API Mock (WireMock)
@WireMockTest
class YouTubeClientTest {
    @Test
    fun `CC 라이선스 검색 성공`() {
        stubFor(get(urlPathEqualTo("/youtube/v3/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody(sampleResponse)))
    }
}
```

---

## 7. Risk Assessment

### 7.1 기술적 위험

| 위험 | 영향도 | 발생 확률 | 완화 전략 |
|------|--------|----------|----------|
| YouTube API 할당량 초과 | HIGH | MEDIUM | 검색 결과 캐싱, LLM 사전 평가로 처리량 최소화 |
| AI API 비용 급증 | HIGH | MEDIUM | 토큰 사용량 모니터링, 일일 한도 설정 |
| 저작권 문제 | HIGH | LOW | CC 라이선스 검증 강화, 원본 출처 명시 |
| FFmpeg 처리 실패 | MEDIUM | MEDIUM | 재시도 로직, 대체 코덱 설정 |
| STT 정확도 저하 | MEDIUM | MEDIUM | 다국어 모델 사용, 후처리 로직 |

### 7.2 운영적 위험

| 위험 | 영향도 | 발생 확률 | 완화 전략 |
|------|--------|----------|----------|
| 배치 실패 누적 | MEDIUM | MEDIUM | 알림 설정, 자동 재시도 |
| 저장소 용량 초과 | LOW | LOW | 임시 파일 정리, S3 수명주기 정책 |
| 품질 저하 콘텐츠 발행 | HIGH | LOW | Option B (모든 콘텐츠 사전 승인) |

---

## 8. Dependencies & Prerequisites

### 8.1 외부 서비스 설정

```yaml
youtube:
  api-key: ${YOUTUBE_API_KEY}

ai:
  vertex:
    project-id: ${GCP_PROJECT_ID}
    location: asia-northeast3
    credentials: ${GOOGLE_APPLICATION_CREDENTIALS}
  openai:
    api-key: ${OPENAI_API_KEY}

aws:
  access-key: ${AWS_ACCESS_KEY_ID}
  secret-key: ${AWS_SECRET_ACCESS_KEY}
  region: ap-northeast-2
  s3:
    bucket: upvy-ai-content
```

### 8.2 시스템 요구사항

```bash
# 필수 설치 항목
- Java 21+
- FFmpeg 6.0+
- yt-dlp (최신 버전)
- MySQL 8.0+
- Redis 7.0+

# Mac 설치 (Homebrew)
brew install ffmpeg yt-dlp

# Ubuntu 설치
apt-get install ffmpeg
pip install yt-dlp
```

---

## 9. Milestones & Checklist

### 9.1 Phase별 마일스톤

| Phase | 목표 | 완료 기준 |
|-------|------|----------|
| Phase 0 | 백엔드 선행 작업 | 시스템 계정 생성 완료 |
| Phase 1 | AI 기반 YouTube 크롤링 | CrawlStep 통합 테스트 통과 |
| Phase 2 | STT 변환 | TranscribeStep 통합 테스트 통과 |
| Phase 3 | LLM 분석 | AnalyzeStep 통합 테스트 통과 |
| Phase 4 | 비디오 편집 | EditStep 통합 테스트 통과 |
| Phase 5 | 품질 검토 | ReviewStep 통합 테스트 통과 |
| Release | 운영 배포 | 전체 파이프라인 E2E 테스트 통과 |

### 9.2 검증 체크리스트

**백엔드 선행 작업**
- [ ] OAuthProvider에 SYSTEM 추가
- [ ] 시스템 계정 생성 SQL 실행
- [ ] 시스템 계정 프로필 생성

**크롤러 구현**
- [ ] 모든 단위 테스트 통과
- [ ] 코드 커버리지 80% 이상
- [ ] 전체 파이프라인 E2E 테스트 통과

**운영 준비**
- [ ] 모니터링 대시보드 구성
- [ ] 알림 설정 완료
- [ ] 운영 매뉴얼 작성

---

## 10. Appendix

### 10.1 참고 자료

- [YouTube Data API v3 Documentation](https://developers.google.com/youtube/v3)
- [Spring Batch Reference](https://docs.spring.io/spring-batch/reference/)
- [Vertex AI Documentation](https://cloud.google.com/vertex-ai/docs)
- [OpenAI API Reference](https://platform.openai.com/docs/api-reference)
- [FFmpeg Documentation](https://ffmpeg.org/documentation.html)

### 10.2 용어 정의

| 용어 | 설명 |
|------|------|
| CC 라이선스 | Creative Commons 라이선스, 저작자가 허용한 범위 내에서 자유롭게 사용 가능 |
| STT | Speech-to-Text, 음성을 텍스트로 변환하는 기술 |
| LLM | Large Language Model, 대규모 언어 모델 (GPT, Gemini 등) |
| 세그먼트 | 영상에서 추출한 핵심 구간 |
| 쇼트폼 | 1분 이내의 짧은 영상 콘텐츠 |

---

> 이 문서는 구현 진행에 따라 업데이트됩니다.
