# Upvy

> **스크롤 시간을 성장 시간으로**

## 프로젝트 소개

Upvy는 **"시간을 녹이는" 숏폼의 재미와 중독성은 그대로, 하지만 그 시간이 성장으로 이어지는 교육 숏폼 플랫폼**입니다.

TikTok, 인스타그램 릴스, 유튜브 숏츠처럼 재미있지만, 스크롤하다 보면 어느새 새로운 것을 배우게 되는 경험을 제공합니다.

### 핵심 철학
- 재미있으면서도 의미있게
- 부담스러운 학습이 아닌, 자연스러운 성장
- 딱딱한 교육이 아닌, 흥미로운 인사이트

### 프로젝트 목표
- **죄책감 없는 스크롤**: 놀면서도 뭔가 얻어가는 기분
- **자연스러운 성장**: 공부한다는 느낌 없이 자연스레 배우기
- **일상 속 학습**: 출퇴근, 쉬는 시간에 부담없이
- **의미있는 시간**: "또 시간 낭비했다" → "오, 이거 몰랐는데!"

---

## Application Screen

|                       로그인화면                        |                        피드 화면                        |                        카테고리 탐색                        |                        프로필 조회                        |                        검색 화면                        |
| :----------------------------------------------------------: | :----------------------------------------------------------: | :----------------------------------------------------------: | :----------------------------------------------------------: | :----------------------------------------------------------: |
| ![login](https://github.com/user-attachments/assets/6832d5d4-f429-4c12-a0da-91b04cd26433) | ![main-feed](https://github.com/user-attachments/assets/cc1c42c4-6684-4058-8043-73440a2191ca) | ![category](https://github.com/user-attachments/assets/2e390728-2b36-4dcb-8105-1ccfb8d4c192) | ![profile](https://github.com/user-attachments/assets/f34b31f5-ce10-4484-be17-f489998674f3) | ![search](https://github.com/user-attachments/assets/e765bfe9-3be5-4105-8451-5af160c12795) |

---

## 프로젝트 구조

```
upvy/
├── upvy-backend/           # Kotlin + Spring WebFlux 백엔드
├── upvy-frontend/          # React Native + Expo 프론트엔드
├── upvy-ai-crawler/        # YouTube 기반 콘텐츠 크롤링 + 백오피스
├── upvy-content-generator/ # n8n 기반 AI 오리지널 콘텐츠 생성
├── manifest-ops/           # Kubernetes 배포 매니페스트
└── docs/                   # 프로젝트 문서
```

---

## 구현된 기능 (v1.0.0)

### 인증 및 사용자 관리
- Google OAuth 2.0 소셜 로그인 (Custom Tabs 기반)
- JWT 기반 인증 시스템 (Access Token 1시간, Refresh Token 14일)
- 프로필 관리 (프로필 사진, 자기소개, 닉네임 수정)
- 회원 탈퇴 및 계정 복원 (Soft Delete)
- 앱 설정 (로그아웃, 약관, 지원)

### 스마트 피드 시스템
- TikTok/Instagram/Youtube Reels 스타일 세로 스크롤 피드 UI
- Item-based Collaborative Filtering 추천 알고리즘
- Redis 기반 피드 캐싱 시스템
- 카테고리별 피드 (PROGRAMMING, DESIGN, LANGUAGE, BUSINESS, FUN, MOTIVATION)
- 사용자 언어 설정 기반 콘텐츠 가중치 적용
- Pull-to-refresh 및 무한 스크롤 (커서 기반)

### 콘텐츠 관리
- 크리에이터 스튜디오 (비디오/사진 업로드)
- AWS S3 Presigned URL 기반 업로드 시스템
- 비디오 트리밍 편집 기능
- 사진 갤러리 (인스타그램 스타일 스와이프)
- 콘텐츠 삭제/수정 기능
- 썸네일 선택 (5개 옵션)

### 소셜 인터랙션
- 좋아요/저장/공유 기능 (Optimistic Update 적용)
- 댓글 시스템 (대댓글, 좋아요, 인기순/최신순 정렬)
- 팔로우/언팔로우 시스템
- 콘텐츠/사용자 신고 기능
- 콘텐츠/사용자 차단 기능 (피드 필터링 연동)

### 탐색 및 검색
- Manticore Search 통합 검색 엔진
- 콘텐츠/크리에이터 검색 (Failover 전략 적용)
- 카테고리 탐색 탭 (Explore)
- 검색 결과 Masonry 그리드 UI

### 알림 시스템
- FCM 기반 푸시 알림
- 알림 센터 UI (읽음 처리, 삭제)
- 카테고리별 알림 설정 (좋아요, 댓글, 팔로우 등)
- 푸시 토큰 관리 API

### 다국어 지원
- 한국어, 영어, 일본어 지원
- 앱 전체 i18n 시스템 구축
- 콘텐츠 언어 선택 및 필터링

### AI 콘텐츠 크롤링 및 편집기 (upvy-ai-crawler)
- Vertex AI (Gemini) 기반 콘텐츠 검색 및 분석
- YouTube 롱폼 → 숏폼 자동 편집 파이프라인
- Google Cloud STT (Chirp) 타임스탬프 기반 세그먼트 추출
- Thymeleaf 백오피스 관리 시스템
- 콘텐츠 승인/거절 워크플로우
- LLM 기반 퀴즈 자동 생성 (n8n 퀴즈 폴백 지원)

### AI 오리지널 콘텐츠 생성 (upvy-content-generator)
- n8n 기반 시각적 워크플로우 오케스트레이션
- Vertex AI Gemini (스크립트 + 퀴즈 생성)
- Vertex AI Imagen 3 / Veo 2 (이미지 + 영상 생성)
- Google Cloud TTS (다국어 음성 합성)
- FFmpeg 기반 영상 합성 서비스 (자막 + 워터마크)
- 일일 11개 콘텐츠 자동 생성 (EN 5개, JA 3개, KO 3개)

### 크리에이터 애널리틱스
- 콘텐츠별 조회수, 좋아요, 댓글, 저장, 공유 통계

---

## 기술 스택

### 백엔드 (upvy-backend)
| 구분 | 기술 |
|------|------|
| 언어 | Kotlin 1.9.x (JDK 17) |
| 프레임워크 | Spring Boot 3.5.x + WebFlux (반응형) |
| 데이터베이스 | MySQL + R2DBC (Reactive) |
| 쿼리 빌더 | JOOQ (타입 안전 SQL) |
| 캐싱 | Redis (Reactive) |
| 스토리지 | AWS S3 + CloudFront (CDN) |
| 인증 | OAuth 2.0, JWT |
| 검색 | Manticore Search |
| 문서화 | Spring REST Docs |
| 테스트 | JUnit 5, MockK, Testcontainers, ArchUnit |
| 정적 분석 | Detekt, Ktlint |

### 프론트엔드 (upvy-frontend)
| 구분 | 기술 |
|------|------|
| 플랫폼 | React Native 0.81.x + Expo 54 |
| 언어 | TypeScript 5.9.x |
| 상태 관리 | Zustand (클라이언트), React Query (서버) |
| 네비게이션 | React Navigation 7.x |
| 스타일링 | NativeWind (Tailwind CSS) |
| 폼 관리 | React Hook Form + Zod |
| 다국어 | i18next + react-i18next |
| 테스트 | Jest + React Native Testing Library |

### AI 크롤러 (upvy-ai-crawler)
| 구분 | 기술 |
|------|------|
| 언어 | Kotlin 1.9.x |
| 프레임워크 | Spring Boot 3.x + Spring Batch 5.x |
| AI (LLM) | Vertex AI Gemini |
| AI (STT) | Google Cloud STT (Chirp) |
| 비디오 처리 | yt-dlp + FFmpeg |
| 저장소 | MySQL (JPA) + AWS S3 |
| 백오피스 | Thymeleaf + Bootstrap 5 |

### AI Content Generator (upvy-content-generator)
| 구분 | 기술 |
|------|------|
| 오케스트레이션 | n8n (Docker) |
| AI (LLM) | Vertex AI Gemini 2.0 Flash |
| AI (이미지) | Vertex AI Imagen 3 |
| AI (영상) | Vertex AI Veo 2 |
| AI (TTS) | Google Cloud TTS |
| 영상 합성 | Kotlin + Spring Boot + FFmpeg |
| 컨테이너 | Docker Compose |

### 인프라 및 DevOps
| 구분 | 기술 |
|------|------|
| 컨테이너 | Docker |
| 오케스트레이션 | Kubernetes |
| CI/CD | GitHub Actions |
| 이미지 레지스트리 | Docker Hub |
| 모니터링 | OpenTelemetry Collector |
| 테스트 커버리지 | JaCoCo |
| 코드 리뷰 | ReviewDog |

---

## 시작하기

### 사전 요구사항
- JDK 17+
- Node.js 18+
- Docker & Docker Compose
- MySQL 8.0+
- Redis 7.0+

### 백엔드 실행
```bash
cd upvy-backend
./gradlew bootRun
```

### 프론트엔드 실행
```bash
cd upvy-frontend
npm install
npm start
```

### AI 크롤러 실행
```bash
cd upvy-ai-crawler
./gradlew bootRun
```

### AI Content Generator 실행
```bash
cd upvy-content-generator
cp .env.example .env
# .env 파일에 GCP 프로젝트 ID 등 설정
docker-compose up -d
# n8n UI: http://localhost:5678
```

---

## AI와의 협업

Upvy는 AI와 개발자의 협업 방식을 실험하고 최적화하는 것을 주요 목표 중 하나로 삼고 있습니다. Claude Code, Gemini 등 AI 개발 보조 도구를 적극 도입하여 효율적인 개발 프로세스를 탐구합니다.

### 인간-AI 협업 모델

- **AI-in-the-Loop (AITL)**: 주요 아키텍처 설계 및 구현 단계
- **Human-in-the-Loop (HITL)**: 핵심 의사결정 및 코드 리뷰
- **Human-on-the-Loop (HOTL)**: 자동화된 작업 모니터링

### AI 코딩 스펙트럼

Upvy는 주요 아키텍처·설계를 제외한 작업에서 **레벨 4 (로컬 자율 에이전트)** 수준의 AI 협업을 목표로 하고, 주요 아키텍처·설계 단계에서는 **레벨 2~3 (블록 수준 완성 ~ 의도 기반 채팅 에이전트)** 수준의 AI 협업을 적용했습니다.

### TDD 워크플로우

AI와의 협업 효율성을 극대화하기 위해 TDD(테스트 주도 개발)를 적극 도입했습니다:
1. 시나리오 기반 테스트 케이스 작성
2. 기능 구현
3. 코드 리뷰 및 수정
4. 리팩토링
5. 테스트 통과 확인

---

## 문서

- [요구사항 명세서](docs/요구사항명세서.md)
- [백엔드 개발 가이드](docs/BACKEND_DEVELOPMENT_GUIDE.md)
- [Git Convention](docs/GIT_CONVENTION.md)
- [AI Crawler README](upvy-ai-crawler/README.md)
- [AI Content Generator README](upvy-content-generator/README.md)

### API 문서

백엔드 빌드 후 `build/docs/asciidoc/index.html`에서 Spring REST Docs 기반 API 문서를 확인할 수 있습니다.

---

## 개발 통계 (v1.0.0)

- **772개 파일** 변경
- **132,919줄** 코드 추가
- **약 280개 커밋**
- **50개 이상 이슈** 해결
- **테스트 커버리지**: 79.33%

---

## 라이선스

이 프로젝트는 [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE) 하에 배포됩니다.

### 주요 조건
- 소스 코드를 자유롭게 열람, 수정, 배포할 수 있습니다.
- 이 코드를 사용하여 네트워크 서비스를 제공할 경우, **수정된 전체 소스 코드를 동일한 라이선스로 공개**해야 합니다.
- 상업적 사용 시에도 위 조건이 적용됩니다.

자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

---

## 팀

Upvy Team - [@12OneTwo12](https://github.com/12OneTwo12)

---

**Upvy - 스크롤 시간을 성장 시간으로**
