# Upvy AI Crawler

YouTube CC 라이선스 콘텐츠를 크롤링하여 AI로 분석하고 숏폼 콘텐츠를 자동 생성하는 Spring Batch 기반 시스템입니다.

## 개요

이 프로젝트는 Upvy 교육 숏폼 플랫폼의 초기 콘텐츠 확보를 위한 AI 콘텐츠 생성 파이프라인입니다.

### 주요 기능

1. **YouTube CC 콘텐츠 크롤링**: YouTube Data API v3 + yt-dlp (CC 라이선스만 검색)
2. **음성-텍스트 변환 (STT)**: Google STT (Chirp) with 타임스탬프 추출
3. **AI 분석**: Vertex AI (Gemini)로 핵심 구간 추출 + 숏폼 적합성 평가
4. **자동 편집**: FFmpeg로 클립 자르기 (LLM 세그먼트 기반), 세로 포맷 변환
5. **품질 검수**: 자동 점수 산정, 70점 이상만 관리자 승인 대기열로
6. **백오피스 관리**: 콘텐츠 승인/거절, 메타데이터 수정, 직접 실행
7. **콘텐츠 게시**: 승인된 콘텐츠를 백엔드 DB에 자동 INSERT

### 품질 평가 기준

- **교육적 가치** (educationalValue): 학습 가치
- **관련성** (relevanceScore): 플랫폼과의 관련성
- **숏폼 적합성** (shortFormSuitability): 빠른 템포, 편집 밀도, 콘텐츠 압축도
- **예상 품질** (predictedQuality): 종합 품질 점수

### 출처 표기

모든 콘텐츠에 CC 라이선스 원본 정보가 자동으로 추가됩니다:
```
📌 출처: 이 콘텐츠는 Creative Commons 라이선스로 공개된 YouTube 영상을 기반으로 제작되었습니다.
원본 제목: "Original Video Title"
원본 링크: https://www.youtube.com/watch?v=xxxxx
```

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어 | Kotlin 1.9.x |
| 프레임워크 | Spring Boot 3.x + Spring Batch 5.x |
| AI (LLM) | Vertex AI Gemini (교체 가능) |
| AI (STT) | Vertex AI STT with Chirp (타임스탬프 지원) |
| 비디오 | yt-dlp + FFmpeg |
| 저장소 | MySQL (JPA) + AWS S3 (단일 버킷 + prefix) |
| 백오피스 | Thymeleaf + Bootstrap 5 |
| 스케줄링 | Spring Scheduler |

## 프로젝트 구조

```
upvy-ai-crawler/
├── .claude/skills/           # Claude 개발 가이드
├── src/
│   ├── main/
│   │   ├── kotlin/me/onetwo/upvy/crawler/
│   │   │   ├── CrawlerApplication.kt
│   │   │   ├── config/           # 설정 클래스
│   │   │   ├── batch/            # Spring Batch Job/Step 구현
│   │   │   │   ├── job/          # Batch Job 정의
│   │   │   │   └── step/         # Step 별 Reader/Processor/Writer
│   │   │   │       ├── crawl/    # Step 1: YouTube 크롤링
│   │   │   │       ├── transcribe/ # Step 2: STT 변환
│   │   │   │       ├── analyze/  # Step 3: LLM 분석
│   │   │   │       ├── edit/     # Step 4: 영상 편집
│   │   │   │       └── review/   # Step 5: 품질 검수
│   │   │   ├── backoffice/       # 백오피스 관리 시스템
│   │   │   │   ├── controller/   # Thymeleaf 컨트롤러
│   │   │   │   ├── service/      # 승인/거절, 게시 서비스
│   │   │   │   ├── domain/       # PendingContent 등
│   │   │   │   └── repository/
│   │   │   ├── client/           # 외부 API 클라이언트
│   │   │   │   ├── llm/          # LLM 클라이언트 (Vertex AI)
│   │   │   │   ├── stt/          # STT 클라이언트 (Vertex AI)
│   │   │   │   ├── youtube/      # YouTube Data API
│   │   │   │   └── video/        # yt-dlp, FFmpeg 래퍼
│   │   │   ├── domain/           # 엔티티, Repository
│   │   │   │   └── content/      # 백엔드 테이블용 Entity
│   │   │   ├── service/          # 공통 서비스 (S3, Quality 등)
│   │   │   └── scheduler/        # 배치 스케줄러
│   │   └── resources/
│   │       ├── application.yml
│   │       └── templates/backoffice/  # Thymeleaf 템플릿
│   └── test/kotlin/
└── build.gradle.kts
```

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      AI Content Batch Job                                │
│                     (매일 새벽 3시 자동 실행)                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐              │
│  │  Step 1  │──>│  Step 2  │──>│  Step 3  │──>│  Step 4  │              │
│  │  Crawl   │   │Transcribe│   │ Analyze  │   │   Edit   │              │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘              │
│       │              │              │              │                     │
│       v              v              v              v                     │
│   YouTube API    Vertex AI     Vertex AI       FFmpeg                   │
│   + yt-dlp       STT Chirp     Gemini LLM      + S3 Upload              │
│   (CC만 검색)    (타임스탬프)   (세그먼트 추출)  (public-read)            │
│                                                                          │
│                           │                                              │
│                           v                                              │
│                    ┌──────────┐                                          │
│                    │  Step 5  │──> 70점 이상 ──> pending_contents        │
│                    │  Review  │                                          │
│                    └──────────┘                                          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                      Backoffice (관리자 UI)                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   /backoffice/pending         승인 대기 콘텐츠 목록                       │
│   /backoffice/pending/{id}    콘텐츠 상세/수정/승인/거절                   │
│   /backoffice/pending/approved  승인된 콘텐츠 히스토리                    │
│   /backoffice/pending/rejected  거절된 콘텐츠 히스토리                    │
│   /backoffice/ai-jobs         AI Job 관리 (단계별 직접 실행)              │
│                                                                          │
│   승인 시:                                                               │
│   pending_contents.APPROVED ──> contents + content_metadata +            │
│                                 content_interactions INSERT              │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## 설정

### 환경 변수

```bash
# Google Cloud (Vertex AI LLM/STT)
export GCP_PROJECT_ID=your-project-id
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json

# YouTube
export YOUTUBE_API_KEY=your-youtube-api-key

# AWS S3
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_REGION=ap-northeast-2
export S3_BUCKET=upvy-ai-media

# Database
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_DATABASE=upvy
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=password

# System
export SYSTEM_USER_ID=00000000-0000-0000-0000-000000000001  # AI 콘텐츠 생성자 ID
```

### S3 버킷 구조

단일 버킷 + prefix 구조:
```
upvy-ai-media/
├── raw-videos/           # 원본 다운로드 영상
├── edited-videos/        # 편집된 숏폼 영상 (public-read)
│   └── clips/{videoId}/{jobId}.mp4
└── thumbnails/           # 썸네일 이미지 (public-read)
    └── {videoId}/{jobId}.jpg
```

**버킷 정책** (edited-videos, thumbnails public read):
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "PublicReadForPublishedContent",
            "Effect": "Allow",
            "Principal": "*",
            "Action": "s3:GetObject",
            "Resource": [
                "arn:aws:s3:::upvy-ai-media/edited-videos/*",
                "arn:aws:s3:::upvy-ai-media/thumbnails/*"
            ]
        }
    ]
}
```

### application.yml 주요 설정

```yaml
ai:
  llm:
    provider: vertex-ai
    project-id: ${GCP_PROJECT_ID}
    model: gemini-1.5-pro
  stt:
    provider: vertex-ai
    project-id: ${GCP_PROJECT_ID}
    encoding: OGG_OPUS

s3:
  bucket: ${S3_BUCKET:upvy-ai-media}
  region: ${AWS_REGION:ap-northeast-2}
  prefix:
    raw-videos: raw-videos
    edited-videos: edited-videos
    thumbnails: thumbnails

crawler:
  system-user-id: ${SYSTEM_USER_ID:00000000-0000-0000-0000-000000000001}

batch:
  schedule:
    cron: "0 0 1 * * *"  # 매일 새벽 1시
```

## 실행

### 빌드

```bash
./gradlew build
```

### 테스트

```bash
./gradlew test
```

### 실행

```bash
./gradlew bootRun
```

### 백오피스 접속

```
http://localhost:8080/backoffice
```

## 개발 가이드

개발 시 `.claude/skills/` 디렉토리의 문서를 참조하세요:

- **핵심 원칙**: `core-principles.md`
- **Spring Batch 패턴**: `spring-batch-guide.md`
- **AI 추상화**: `ai-abstraction.md`
- **테스트 가이드**: `testing-guide.md`
- **코드 스타일**: `code-style.md`
- **Git Convention**: `git.md`
- **빠른 참조**: `quick-reference.md`

## 관련 이슈

- [#14 AI 콘텐츠 생성 시스템 구현](https://github.com/12OneTwo12/upvy/issues/14)

## 라이선스

이 프로젝트는 Upvy 내부 프로젝트입니다.
