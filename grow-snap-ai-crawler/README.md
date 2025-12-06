# GrowSnap AI Crawler

YouTube CC 라이선스 콘텐츠를 크롤링하여 AI로 분석하고 숏폼 콘텐츠를 자동 생성하는 Spring Batch 기반 시스템입니다.

## 개요

이 프로젝트는 GrowSnap 플랫폼의 초기 콘텐츠 확보를 위한 AI 콘텐츠 생성 파이프라인입니다.

### 주요 기능

1. **YouTube CC 콘텐츠 크롤링**: YouTube Data API v3 + yt-dlp
2. **음성-텍스트 변환 (STT)**: Google STT (Chirp) / OpenAI Whisper
3. **AI 분석**: Vertex AI (Gemini) / OpenAI (GPT-4)로 핵심 구간 추출
4. **자동 편집**: FFmpeg로 클립 자르기, 자막 추가, 크레딧 삽입
5. **품질 검수**: 자동 점수 산정, 70점 이상만 관리자 승인 대기열로

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어 | Kotlin 1.9.x |
| 프레임워크 | Spring Boot 3.x + Spring Batch 5.x |
| AI (LLM) | Vertex AI Gemini / OpenAI GPT-4 (교체 가능) |
| AI (STT) | Google STT Chirp / OpenAI Whisper (교체 가능) |
| 비디오 | yt-dlp + FFmpeg |
| 저장소 | MySQL + AWS S3 |
| 스케줄링 | Spring Scheduler |

## 프로젝트 구조

```
grow-snap-ai-crawler/
├── .claude/skills/           # Claude 개발 가이드
│   ├── README.md             # Skills 개요
│   ├── core-principles.md    # 핵심 원칙 (TDD, SOLID)
│   ├── spring-batch-guide.md # Spring Batch 패턴
│   ├── ai-abstraction.md     # AI 추상화 레이어
│   ├── testing-guide.md      # 테스트 가이드
│   ├── code-style.md         # 코드 스타일
│   ├── git.md                # Git Convention
│   └── quick-reference.md    # 빠른 참조
├── src/
│   ├── main/
│   │   ├── kotlin/me/onetwo/growsnap/crawler/
│   │   │   ├── CrawlerApplication.kt
│   │   │   ├── config/       # 설정 클래스
│   │   │   ├── pipeline/     # Spring Batch Step 구현
│   │   │   ├── client/       # AI 클라이언트, 외부 API 클라이언트
│   │   │   ├── domain/       # 엔티티, Repository
│   │   │   └── scheduler/    # 스케줄러
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-prod.yml
│   └── test/kotlin/
└── build.gradle.kts
```

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                    AI Content Batch Job                         │
│                   (매일 새벽 3시 실행)                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐     │
│  │  Step 1  │──>│  Step 2  │──>│  Step 3  │──>│  Step 4  │     │
│  │  Crawl   │   │Transcribe│   │ Analyze  │   │   Edit   │     │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘     │
│       │              │              │              │            │
│       v              v              v              v            │
│   YouTube API    SttClient      LlmClient       FFmpeg         │
│   + yt-dlp       (추상화)        (추상화)                        │
│                                                                 │
│                           │                                     │
│                           v                                     │
│                    ┌──────────┐                                 │
│                    │  Step 5  │──> 70점 이상 ──> [관리자 승인]   │
│                    │  Review  │                                 │
│                    └──────────┘                                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 설정

### 환경 변수

```bash
# Google Cloud (Vertex AI, Google STT)
export GCP_PROJECT_ID=your-project-id
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json

# OpenAI (대안)
export OPENAI_API_KEY=your-openai-api-key

# YouTube
export YOUTUBE_API_KEY=your-youtube-api-key

# AWS S3
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_REGION=ap-northeast-2

# Database
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_DATABASE=growsnap
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=password
```

### application.yml

```yaml
ai:
  llm:
    provider: vertex          # vertex | openai
    model: gemini-1.5-pro
  stt:
    provider: google          # google | whisper
    model: chirp

vertex:
  project-id: ${GCP_PROJECT_ID}
  location: asia-northeast3   # 서울 리전

spring:
  batch:
    jdbc:
      initialize-schema: always
    job:
      enabled: false          # 스케줄러로 실행

  datasource:
    url: jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}
    username: ${MYSQL_USERNAME}
    password: ${MYSQL_PASSWORD}
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

### Docker

```bash
docker build -t grow-snap-ai-crawler .
docker run -e GCP_PROJECT_ID=xxx -e YOUTUBE_API_KEY=xxx grow-snap-ai-crawler
```

## AI 제공자 교체

이 프로젝트는 인터페이스 기반 설계로 AI 제공자를 쉽게 교체할 수 있습니다.

### Vertex AI -> OpenAI 변경

```yaml
# application.yml
ai:
  llm:
    provider: openai    # vertex -> openai 변경
    model: gpt-4o
  stt:
    provider: whisper   # google -> whisper 변경
    model: whisper-1
```

### 새로운 AI 제공자 추가

1. `LlmClient` 또는 `SttClient` 인터페이스 구현
2. `@ConditionalOnProperty` 어노테이션으로 Bean 등록
3. application.yml에서 provider 설정

자세한 내용은 `.claude/skills/ai-abstraction.md` 참조

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

- [#14 AI 콘텐츠 생성 시스템 구현](https://github.com/12OneTwo12/grow-snap/issues/14)

## 라이선스

이 프로젝트는 GrowSnap 내부 프로젝트입니다.
