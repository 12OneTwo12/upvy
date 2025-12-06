# AI Content Crawler - 구현 계획서

> 작성일: 2024-12-06
> 버전: 1.0
> 관련 이슈: [#14 AI 콘텐츠 생성 시스템 구현](https://github.com/12OneTwo12/grow-snap/issues/14)

---

## 1. Executive Summary

### 1.1 목적
YouTube CC 라이선스 콘텐츠를 자동으로 수집, 분석, 편집하여 교육용 쇼트폼 콘텐츠를 생성하는 배치 시스템 구현

### 1.2 핵심 목표
- **자동화**: 사람 개입 최소화 (품질 검토 단계만 수동)
- **품질 보장**: AI 기반 품질 점수 + 관리자 승인 이중 검증
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
│  │  Publish    │───▶ grow-snap-backend DB                          │
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
├── Step 1: crawlStep (YouTube 비디오 검색)
│   ├── ItemReader: YouTubeSearchReader
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
└── Step 5: reviewStep (품질 검토 및 발행 준비)
    ├── ItemReader: EditedJobReader
    ├── ItemProcessor: QualityReviewProcessor
    └── ItemWriter: ReviewResultWriter
```

### 2.3 AI 추상화 레이어

```kotlin
// LLM 제공자 추상화
interface LlmClient {
    suspend fun analyze(prompt: String): String
    suspend fun extractKeySegments(transcript: String): List<Segment>
    suspend fun generateMetadata(content: String): ContentMetadata
}

// STT 제공자 추상화
interface SttClient {
    suspend fun transcribe(audioUrl: String): TranscriptResult
}

// 전략 패턴으로 구현체 선택
// application.yml의 ai.provider 설정에 따라 자동 선택
```

---

## 3. Implementation Phases

### Phase 1: 인프라 및 YouTube 크롤링 (Step 1)

#### 3.1.1 작업 목록

| ID | 작업 | 파일 | 우선순위 |
|----|------|------|----------|
| P1-01 | YouTube Data API v3 클라이언트 구현 | `client/youtube/YouTubeClient.kt` | HIGH |
| P1-02 | CC 라이선스 비디오 검색 로직 | `client/youtube/YouTubeSearchService.kt` | HIGH |
| P1-03 | CrawlStep Reader 구현 | `batch/step/crawl/YouTubeSearchReader.kt` | HIGH |
| P1-04 | CrawlStep Processor 구현 | `batch/step/crawl/VideoCandidateProcessor.kt` | HIGH |
| P1-05 | CrawlStep Writer 구현 | `batch/step/crawl/AiContentJobWriter.kt` | HIGH |
| P1-06 | CrawlStep 통합 설정 | `batch/config/CrawlStepConfig.kt` | HIGH |
| P1-07 | yt-dlp 래퍼 구현 | `client/video/YtDlpWrapper.kt` | MEDIUM |
| P1-08 | 비디오 다운로드 서비스 | `service/VideoDownloadService.kt` | MEDIUM |
| P1-09 | 단위 테스트 작성 | `test/.../crawl/*Test.kt` | HIGH |
| P1-10 | 통합 테스트 작성 | `test/.../CrawlStepIntegrationTest.kt` | MEDIUM |

#### 3.1.2 데이터 모델

```kotlin
// YouTube API 검색 요청
data class YouTubeSearchRequest(
    val query: String,              // 검색 키워드
    val maxResults: Int = 10,       // 최대 결과 수
    val publishedAfter: String?,    // 게시일 필터
    val videoDuration: String?,     // 영상 길이 필터 (short, medium, long)
    val videoLicense: String = "creativeCommon"  // CC 라이선스만
)

// 검색 결과
data class VideoCandidate(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelTitle: String?,
    val description: String?,
    val publishedAt: String?,
    val duration: String?,          // ISO 8601 (PT1H2M3S)
    val thumbnailUrl: String?
)
```

#### 3.1.3 API 쿼터 관리

```yaml
# YouTube Data API 쿼터: 10,000 units/day
# - search.list: 100 units
# - videos.list: 1 unit

# 일일 최대 검색 횟수: ~100회
# 전략:
#   - 배치당 검색 10회로 제한
#   - 검색 결과 캐싱 (Redis, TTL 24h)
#   - 중복 비디오 필터링
```

#### 3.1.4 테스트 케이스

```kotlin
// 단위 테스트
class YouTubeClientTest {
    @Test fun `CC 라이선스 비디오만 검색되어야 함`()
    @Test fun `검색 결과가 없을 때 빈 리스트 반환`()
    @Test fun `API 할당량 초과 시 예외 처리`()
}

class VideoCandidateProcessorTest {
    @Test fun `이미 처리된 비디오는 필터링되어야 함`()
    @Test fun `영상 길이가 기준 미달이면 필터링`()
    @Test fun `적합한 비디오는 AiContentJob으로 변환`()
}
```

---

### Phase 2: 음성-텍스트 변환 (Step 2)

#### 3.2.1 작업 목록

| ID | 작업 | 파일 | 우선순위 |
|----|------|------|----------|
| P2-01 | SttClient 인터페이스 정의 | `client/SttClient.kt` | HIGH |
| P2-02 | WhisperSttClient 구현 | `client/stt/WhisperSttClient.kt` | HIGH |
| P2-03 | VertexAiSttClient 구현 | `client/stt/VertexAiSttClient.kt` | MEDIUM |
| P2-04 | 오디오 추출 서비스 | `service/AudioExtractService.kt` | HIGH |
| P2-05 | TranscribeStep Reader | `batch/step/transcribe/PendingJobReader.kt` | HIGH |
| P2-06 | TranscribeStep Processor | `batch/step/transcribe/TranscribeProcessor.kt` | HIGH |
| P2-07 | TranscribeStep Writer | `batch/step/transcribe/TranscriptWriter.kt` | HIGH |
| P2-08 | TranscribeStep 설정 | `batch/config/TranscribeStepConfig.kt` | HIGH |
| P2-09 | 단위/통합 테스트 | `test/.../transcribe/*Test.kt` | HIGH |

#### 3.2.2 Whisper API 통합

```kotlin
// Whisper API 요청 형식
data class WhisperRequest(
    val audioUrl: String,
    val language: String = "ko",      // 기본 한국어
    val responseFormat: String = "verbose_json",
    val timestampGranularities: List<String> = listOf("segment")
)

// 응답 파싱
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

#### 3.2.3 오디오 처리 파이프라인

```
1. 비디오 파일 → FFmpeg → 오디오 추출 (MP3/WAV)
2. 오디오 파일 → S3 업로드 (임시 저장)
3. S3 URL → Whisper API → TranscriptResult
4. 결과 저장 → DB 업데이트
5. 임시 오디오 파일 → 정리 (cleanup)
```

---

### Phase 3: LLM 분석 및 세그먼트 추출 (Step 3)

#### 3.3.1 작업 목록

| ID | 작업 | 파일 | 우선순위 |
|----|------|------|----------|
| P3-01 | LlmClient 인터페이스 정의 | `client/LlmClient.kt` | HIGH |
| P3-02 | VertexAiLlmClient 구현 | `client/llm/VertexAiLlmClient.kt` | HIGH |
| P3-03 | OpenAiLlmClient 구현 | `client/llm/OpenAiLlmClient.kt` | MEDIUM |
| P3-04 | 프롬프트 템플릿 관리 | `prompt/PromptTemplates.kt` | HIGH |
| P3-05 | AnalyzeStep Reader | `batch/step/analyze/TranscribedJobReader.kt` | HIGH |
| P3-06 | AnalyzeStep Processor | `batch/step/analyze/LlmAnalyzeProcessor.kt` | HIGH |
| P3-07 | AnalyzeStep Writer | `batch/step/analyze/AnalysisWriter.kt` | HIGH |
| P3-08 | AnalyzeStep 설정 | `batch/config/AnalyzeStepConfig.kt` | HIGH |
| P3-09 | 단위/통합 테스트 | `test/.../analyze/*Test.kt` | HIGH |

#### 3.3.2 LLM 프롬프트 설계

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

        JSON 형식으로만 응답하세요:
    """.trimIndent()

    val METADATA_GENERATION = """
        다음 영상 정보를 바탕으로 SNS 쇼트폼 콘텐츠 메타데이터를 생성해주세요.

        원본 제목: {{originalTitle}}
        원본 설명: {{originalDescription}}
        추출된 구간: {{segment}}

        다음 형식으로 응답해주세요:
        - title: 매력적인 제목 (30자 이내, 이모지 포함)
        - description: 설명 (100자 이내)
        - tags: 해시태그 (5개)
        - category: 카테고리
        - difficulty: 난이도 (BEGINNER/INTERMEDIATE/ADVANCED)
    """.trimIndent()
}
```

#### 3.3.3 LLM 응답 파싱

```kotlin
data class SegmentExtractionResponse(
    val segments: List<Segment>
)

data class Segment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val title: String,
    val description: String?,
    val keywords: List<String>,
    val qualityScore: Int
)

// JSON 파싱 유틸리티
class LlmResponseParser {
    fun parseSegments(response: String): List<Segment>
    fun parseMetadata(response: String): ContentMetadata
}
```

---

### Phase 4: 비디오 편집 (Step 4)

#### 3.4.1 작업 목록

| ID | 작업 | 파일 | 우선순위 |
|----|------|------|----------|
| P4-01 | FFmpegWrapper 구현 | `client/video/FFmpegWrapper.kt` | HIGH |
| P4-02 | 비디오 클리핑 서비스 | `service/VideoClipService.kt` | HIGH |
| P4-03 | 썸네일 생성 서비스 | `service/ThumbnailService.kt` | MEDIUM |
| P4-04 | S3 업로드 서비스 | `service/S3UploadService.kt` | HIGH |
| P4-05 | EditStep Reader | `batch/step/edit/AnalyzedJobReader.kt` | HIGH |
| P4-06 | EditStep Processor | `batch/step/edit/VideoEditProcessor.kt` | HIGH |
| P4-07 | EditStep Writer | `batch/step/edit/EditedVideoWriter.kt` | HIGH |
| P4-08 | EditStep 설정 | `batch/config/EditStepConfig.kt` | HIGH |
| P4-09 | 단위/통합 테스트 | `test/.../edit/*Test.kt` | HIGH |

#### 3.4.2 FFmpeg 명령어 템플릿

```kotlin
object FFmpegCommands {

    // 구간 클리핑
    fun clip(input: String, output: String, startMs: Long, endMs: Long): List<String> {
        val startSec = startMs / 1000.0
        val durationSec = (endMs - startMs) / 1000.0
        return listOf(
            "ffmpeg", "-i", input,
            "-ss", startSec.toString(),
            "-t", durationSec.toString(),
            "-c:v", "libx264",
            "-c:a", "aac",
            "-y", output
        )
    }

    // 썸네일 추출
    fun thumbnail(input: String, output: String, timeMs: Long): List<String> {
        val timeSec = timeMs / 1000.0
        return listOf(
            "ffmpeg", "-i", input,
            "-ss", timeSec.toString(),
            "-vframes", "1",
            "-q:v", "2",
            "-y", output
        )
    }

    // 세로 리사이징 (9:16 쇼츠 포맷)
    fun resizeVertical(input: String, output: String): List<String> {
        return listOf(
            "ffmpeg", "-i", input,
            "-vf", "scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2",
            "-c:a", "copy",
            "-y", output
        )
    }
}
```

#### 3.4.3 S3 구조

```
s3://grow-snap-ai-content/
├── raw/                      # 원본 다운로드
│   └── {videoId}/
│       └── video.mp4
├── clips/                    # 편집된 클립
│   └── {jobId}/
│       └── {segmentId}.mp4
├── thumbnails/               # 썸네일
│   └── {jobId}/
│       └── {segmentId}.jpg
└── temp/                     # 임시 파일 (자동 정리)
    └── audio/
        └── {jobId}.mp3
```

---

### Phase 5: 품질 검토 및 발행 (Step 5)

#### 3.5.1 작업 목록

| ID | 작업 | 파일 | 우선순위 |
|----|------|------|----------|
| P5-01 | 품질 점수 계산 서비스 | `service/QualityScoreService.kt` | HIGH |
| P5-02 | ReviewStep Reader | `batch/step/review/EditedJobReader.kt` | HIGH |
| P5-03 | ReviewStep Processor | `batch/step/review/QualityReviewProcessor.kt` | HIGH |
| P5-04 | ReviewStep Writer | `batch/step/review/ReviewResultWriter.kt` | HIGH |
| P5-05 | ReviewStep 설정 | `batch/config/ReviewStepConfig.kt` | HIGH |
| P5-06 | 관리자 승인 API | 백엔드 연동 | MEDIUM |
| P5-07 | 발행 서비스 | `service/PublishService.kt` | HIGH |
| P5-08 | 단위/통합 테스트 | `test/.../review/*Test.kt` | HIGH |

#### 3.5.2 품질 점수 산정 기준

```kotlin
data class QualityScore(
    val totalScore: Int,              // 종합 점수 (0-100)
    val contentRelevance: Int,        // 콘텐츠 관련성 (0-25)
    val audioClarity: Int,            // 오디오 명확성 (0-25)
    val visualQuality: Int,           // 영상 품질 (0-25)
    val educationalValue: Int         // 교육적 가치 (0-25)
)

// 자동 처리 기준
// - 70점 이상: 관리자 승인 대기열로 이동
// - 50-69점: 수동 검토 필요 (NEEDS_REVIEW)
// - 50점 미만: 자동 거절 (REJECTED)
```

#### 3.5.3 백엔드 연동

```kotlin
// 승인된 콘텐츠 → 백엔드 content 테이블에 삽입
// 공유 DB 사용으로 직접 삽입 가능

@Entity
@Table(name = "content")
class Content(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val userId: Long,                 // AI 크리에이터 계정 ID
    val title: String,
    val description: String,
    val contentUrl: String,           // S3 URL
    val thumbnailUrl: String,
    val category: String,
    val tags: String,                 // JSON array
    val status: String = "PUBLISHED",
    val sourceType: String = "AI_GENERATED",
    val originalVideoId: String?,     // YouTube 원본 ID

    // Audit Trail
    val createdAt: LocalDateTime,
    val createdBy: String = "system",
    val updatedAt: LocalDateTime,
    val updatedBy: String = "system",
    val deletedAt: LocalDateTime? = null
)
```

---

## 4. Database Schema

### 4.1 테이블 설계

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
    error_message TEXT,
    retry_count INT DEFAULT 0,

    -- Audit Trail
    created_at DATETIME NOT NULL,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    deleted_at DATETIME,

    INDEX idx_status (status),
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

-- 검색 키워드 관리 테이블
CREATE TABLE ai_search_keyword (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    priority INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    last_searched_at DATETIME,

    -- Audit Trail
    created_at DATETIME NOT NULL,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    deleted_at DATETIME,

    UNIQUE INDEX idx_keyword (keyword)
);
```

### 4.2 상태 전이 다이어그램

```
PENDING → CRAWLED → TRANSCRIBED → ANALYZED → EDITED → PENDING_APPROVAL
   ↓          ↓           ↓            ↓          ↓              ↓
FAILED    FAILED     FAILED      FAILED    FAILED       APPROVED/REJECTED
                                                              ↓
                                                         PUBLISHED
```

---

## 5. Testing Strategy

### 5.1 테스트 피라미드

```
                    ┌───────────────────┐
                    │   E2E Tests (5%)   │  ← 전체 파이프라인 테스트
                    ├───────────────────┤
                    │ Integration (25%)  │  ← Step 통합 테스트
                    ├───────────────────┤
                    │   Unit Tests (70%) │  ← 개별 컴포넌트 테스트
                    └───────────────────┘
```

### 5.2 테스트 범위

| 영역 | 커버리지 목표 | 필수 테스트 |
|------|--------------|------------|
| Client 레이어 | 80%+ | API 호출, 에러 핸들링 |
| Service 레이어 | 90%+ | 비즈니스 로직 |
| Batch Step | 85%+ | Reader/Processor/Writer |
| Domain | 95%+ | 엔티티, 값 객체 |

### 5.3 Mock 전략

```kotlin
// AI 클라이언트 Mock
class MockLlmClient : LlmClient {
    override suspend fun analyze(prompt: String): String {
        return """{"segments": [...]}"""
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

## 6. Risk Assessment

### 6.1 기술적 위험

| 위험 | 영향도 | 발생 확률 | 완화 전략 |
|------|--------|----------|----------|
| YouTube API 할당량 초과 | HIGH | MEDIUM | 검색 결과 캐싱, 일일 제한 설정 |
| AI API 비용 급증 | HIGH | MEDIUM | 토큰 사용량 모니터링, 일일 한도 설정 |
| 저작권 문제 | HIGH | LOW | CC 라이선스 검증 강화, 원본 출처 명시 |
| FFmpeg 처리 실패 | MEDIUM | MEDIUM | 재시도 로직, 대체 코덱 설정 |
| STT 정확도 저하 | MEDIUM | MEDIUM | 다국어 모델 사용, 후처리 로직 |

### 6.2 운영적 위험

| 위험 | 영향도 | 발생 확률 | 완화 전략 |
|------|--------|----------|----------|
| 배치 실패 누적 | MEDIUM | MEDIUM | 알림 설정, 자동 재시도 |
| 저장소 용량 초과 | LOW | LOW | 임시 파일 정리, S3 수명주기 정책 |
| 품질 저하 콘텐츠 발행 | HIGH | LOW | 이중 검토 (AI + 관리자) |

---

## 7. Dependencies & Prerequisites

### 7.1 외부 서비스 설정

```yaml
# 필요한 API 키 및 자격 증명
youtube:
  api-key: ${YOUTUBE_API_KEY}  # Google Cloud Console에서 발급

ai:
  vertex:
    project-id: ${GCP_PROJECT_ID}
    location: asia-northeast3
    credentials: ${GOOGLE_APPLICATION_CREDENTIALS}  # 서비스 계정 JSON

  openai:
    api-key: ${OPENAI_API_KEY}

aws:
  access-key: ${AWS_ACCESS_KEY_ID}
  secret-key: ${AWS_SECRET_ACCESS_KEY}
  region: ap-northeast-2
  s3:
    bucket: grow-snap-ai-content
```

### 7.2 시스템 요구사항

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

## 8. Milestones

### 8.1 Phase별 마일스톤

| Phase | 목표 | 완료 기준 |
|-------|------|----------|
| Phase 1 | YouTube 크롤링 | CrawlStep 통합 테스트 통과 |
| Phase 2 | STT 변환 | TranscribeStep 통합 테스트 통과 |
| Phase 3 | LLM 분석 | AnalyzeStep 통합 테스트 통과 |
| Phase 4 | 비디오 편집 | EditStep 통합 테스트 통과 |
| Phase 5 | 품질 검토 | ReviewStep 통합 테스트 통과 |
| Release | 운영 배포 | 전체 파이프라인 E2E 테스트 통과 |

### 8.2 검증 체크리스트

- [ ] 모든 단위 테스트 통과
- [ ] 코드 커버리지 80% 이상
- [ ] API 문서 작성 완료
- [ ] 운영 매뉴얼 작성
- [ ] 모니터링 대시보드 구성
- [ ] 알림 설정 완료

---

## 9. Appendix

### 9.1 참고 자료

- [YouTube Data API v3 Documentation](https://developers.google.com/youtube/v3)
- [Spring Batch Reference](https://docs.spring.io/spring-batch/reference/)
- [Vertex AI Documentation](https://cloud.google.com/vertex-ai/docs)
- [OpenAI API Reference](https://platform.openai.com/docs/api-reference)
- [FFmpeg Documentation](https://ffmpeg.org/documentation.html)

### 9.2 용어 정의

| 용어 | 설명 |
|------|------|
| CC 라이선스 | Creative Commons 라이선스, 저작자가 허용한 범위 내에서 자유롭게 사용 가능 |
| STT | Speech-to-Text, 음성을 텍스트로 변환하는 기술 |
| LLM | Large Language Model, 대규모 언어 모델 (GPT, Gemini 등) |
| 세그먼트 | 영상에서 추출한 핵심 구간 |
| 쇼트폼 | 1분 이내의 짧은 영상 콘텐츠 |

---

> 이 문서는 구현 진행에 따라 업데이트됩니다.
