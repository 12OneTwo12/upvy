# Upvy AI Crawler Skills 가이드

> AI 콘텐츠 크롤러 개발을 위한 Skill 문서 모음입니다.

## 개요

이 프로젝트는 **Spring Batch** 기반의 AI 콘텐츠 생성 시스템입니다.
YouTube CC 라이선스 콘텐츠를 수집하여 AI로 분석하고 숏폼 콘텐츠를 자동 생성합니다.

### 프로젝트 특성

| 구분 | 내용 |
|------|------|
| 프레임워크 | Spring Boot 3.x + Spring Batch 5.x |
| 언어 | Kotlin 1.9.x |
| AI 추상화 | 인터페이스 기반 (LlmClient, SttClient) |
| 기본 AI | Vertex AI (Gemini + Google STT) |
| 스케줄링 | @Scheduled (매일 새벽 3시) |

## Skill 목록

### 1. core-principles.md (핵심 원칙)

**내용**: TDD, SOLID, Audit Trail, 문서화 필수 규칙

**사용 시기**:
- 모든 개발 작업 시작 전
- 새로운 기능 개발 시
- 코드 리뷰 시

### 2. spring-batch-guide.md (Spring Batch 가이드)

**내용**: Job, Step, ItemReader/Processor/Writer 패턴, 재시도, 에러 핸들링

**사용 시기**:
- 새로운 배치 Step 개발 시
- 배치 Job 구성 시
- 재시도/스킵 로직 구현 시

### 3. ai-abstraction.md (AI 추상화 레이어)

**내용**: LlmClient, SttClient 인터페이스, 구현체 교체 방법

**사용 시기**:
- AI 클라이언트 구현 시
- 새로운 AI 제공자 추가 시
- AI 관련 설정 변경 시

### 4. testing-guide.md (테스트 작성)

**내용**: Step 테스트, Job 테스트, Mock AI 클라이언트 테스트

**사용 시기**:
- 테스트 코드 작성 시
- 배치 통합 테스트 작성 시

### 5. code-style.md (코드 스타일)

**내용**: 로깅, 네이밍, Kotlin 특성 활용

**사용 시기**:
- 코드 작성 시
- 코드 리뷰 시

### 6. git.md (Git Convention)

**내용**: 커밋 메시지 형식, 브랜치 전략, PR 규칙

**사용 시기**:
- 커밋 작성 시
- PR 생성 시

### 7. quick-reference.md (빠른 참조)

**내용**: 체크리스트, 핵심 규칙 요약

**사용 시기**:
- 모든 작업 시작 전
- 빠른 참조가 필요할 때

## 작업별 필수 Skill 매핑

| 작업 유형 | 필수 Skill |
|---------|-----------|
| **새로운 배치 Step 개발** | core-principles -> spring-batch-guide -> testing-guide |
| **AI 클라이언트 추가** | ai-abstraction -> testing-guide |
| **테스트 작성** | testing-guide -> core-principles |
| **코드 리뷰** | quick-reference |
| **커밋/PR** | git |

## 핵심 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    AI Content Batch Job                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐ │
│  │  Step 1  │──>│  Step 2  │──>│  Step 3  │──>│  Step 4  │ │
│  │  Crawl   │   │Transcribe│   │ Analyze  │   │   Edit   │ │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘ │
│       │              │              │              │        │
│       v              v              v              v        │
│   YouTube API    SttClient      LlmClient       FFmpeg     │
│   + yt-dlp       (추상화)        (추상화)                    │
│                                                             │
│                 ┌─────────────────────────────┐            │
│                 │      AI Abstraction Layer   │            │
│                 ├─────────────────────────────┤            │
│                 │  LlmClient    │  SttClient  │            │
│                 │  (interface)  │  (interface)│            │
│                 └───────┬───────┴──────┬──────┘            │
│                         │              │                    │
│         ┌───────────────┼──────────────┼───────────────┐   │
│         v               v              v               v   │
│    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐│
│    │ Vertex  │    │ OpenAI  │    │ Google  │    │ Whisper ││
│    │ Gemini  │    │ GPT-4   │    │   STT   │    │   STT   ││
│    └─────────┘    └─────────┘    └─────────┘    └─────────┘│
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 정리

**모든 개발 작업 시 이 README를 먼저 읽고 필요한 Skill을 선택하세요!**

1. **작업별 필수 Skill 매핑 표** 확인
2. **필요한 Skill만 선택적으로 로드**
3. **Spring Batch 패턴 준수**
4. **AI 추상화 레이어 활용**
