# Upvy AI Content Generator

> AI 기반 자체 교육용 숏폼 콘텐츠 자동 생성 파이프라인

## 개요

YouTube 크롤링이 아닌 **100% AI 오리지널** 교육용 숏폼 콘텐츠를 자동 생성합니다.

### 핵심 특징

- **n8n 기반**: 시각적 워크플로우로 파이프라인 관리
- **Vertex AI 통합**: Gemini (LLM) + Imagen 3 (이미지) + Veo 2 (영상)
- **멀티플랫폼 게시**: Upvy, YouTube Shorts, Instagram Reels, TikTok
- **퀴즈 자동 생성**: 교육 효과 강화를 위한 4지선다 퀴즈

### 일일 생성 목표

| 언어 | 일일 | 월간 | 스케줄 |
|------|------|------|--------|
| 영어 (EN) | 5개 | 150개 | 6, 9, 12, 15, 18시 |
| 일본어 (JA) | 3개 | 90개 | 7, 13, 19시 |
| 한국어 (KO) | 3개 | 90개 | 8, 14, 20시 |
| **합계** | **11개** | **330개** | |

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                            n8n                                   │
│                  (All-in-One Orchestrator)                      │
│                                                                  │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│   │ Vertex AI    │  │ Vertex AI    │  │ Google TTS   │         │
│   │ Gemini       │  │ Imagen/Veo   │  │              │         │
│   └──────────────┘  └──────────────┘  └──────────────┘         │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Compose Service (:8084)                       │
│                    (FFmpeg 영상 합성)                             │
└─────────────────────────────────────────────────────────────────┘
```

## 5단계 파이프라인

```
TOPIC SELECT → SCRIPT+QUIZ → VISUAL+AUDIO → COMPOSE → PUBLISH
     │              │              │            │          │
     ↓              ↓              ↓            ↓          ↓
  Gemini +      스크립트 +      Imagen 3     FFmpeg     Upvy +
  Grounding     퀴즈 생성       Veo 2        합성       SNS
               품질 게이트      TTS (병렬)
```

## 빠른 시작

### 1. 환경 설정

```bash
# .env 파일 생성
cp .env.example .env

# GCP 서비스 계정 키 복사
cp /path/to/your/gcp-key.json credentials/gcp-key.json

# 환경 변수 편집
vim .env
```

### 2. 실행

```bash
docker-compose up -d
```

### 3. 접속

- **n8n UI**: http://localhost:5678
- **Compose API**: http://localhost:8084

## 프로젝트 구조

```
upvy-content-generator/
├── docker-compose.yml       # 전체 서비스 정의
├── .env.example             # 환경 변수 템플릿
├── credentials/             # GCP 서비스 계정 키 (gitignore)
├── compose-service/         # FFmpeg 영상 합성 서비스
│   ├── Dockerfile
│   ├── build.gradle.kts
│   └── src/main/kotlin/...
├── n8n-workflows/           # n8n 워크플로우 export
│   └── content-generator.json
└── PLANNING.md              # 상세 기획서
```

## 비용 예측

| 전략 | 월 비용 (11개/일) |
|------|-------------------|
| 이미지 슬라이드쇼 | ~$551 (~74만원) |
| AI 비디오 (Veo) | ~$924 (~124만원) |
| **하이브리드 (권장)** | **~$738 (~99만원)** |

## 관련 이슈

- [#207 Epic: AI Content Generator 파이프라인 구축](https://github.com/12OneTwo12/upvy/issues/207)
- [#208 Phase 1-1: 프로젝트 구조 및 n8n 환경 구성](https://github.com/12OneTwo12/upvy/issues/208)

## 참고 문서

- [PLANNING.md](./PLANNING.md) - 상세 기획서
- [n8n 공식 문서](https://docs.n8n.io/)
- [Vertex AI 문서](https://cloud.google.com/vertex-ai/docs)
