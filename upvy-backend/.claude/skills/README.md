# Upvy Backend Skills 가이드

> CLAUDE.md를 8개의 Skill로 분리하여 토큰 효율성을 개선했습니다.

## 개요

기존 CLAUDE.md 파일(2,144줄, 약 40,000 토큰)을 8개의 독립적인 Skill 파일로 분리하여 필요한 규칙만 선택적으로 로드할 수 있도록 개선했습니다.

### 토큰 효율성

| 사용 방식 | 토큰 사용량 | 절감 효과 |
|----------|-----------|---------|
| **기존 CLAUDE.md** | ~40,000 토큰 | - |
| **Skill 사용** | ~2,000-10,000 토큰 | **75-95% ↓** |

## Skill 목록

### 1. core-principles.md (핵심 원칙)

**내용**: TDD, SOLID, Audit Trail, 문서화 필수 규칙

**사용 시기**:
- 모든 개발 작업 시작 전
- 새로운 기능 개발 시
- 코드 리뷰 시

**토큰**: ~2,500

### 2. mvc-layers.md (MVC 계층)

**내용**: Controller, Service, Repository 계층별 역할과 책임

**사용 시기**:
- 새로운 API 개발 시
- 계층 간 책임 분리 확인 시
- Principal 추출 방법 확인 시

**토큰**: ~3,500

### 3. testing-guide.md (테스트 작성)

**내용**: Controller, Service, Repository 테스트 템플릿과 규칙

**사용 시기**:
- 테스트 코드 작성 시
- mockUser() 사용법 확인 시
- Repository 통합 테스트 작성 시

**토큰**: ~3,000

### 4. api-design.md (REST API 설계)

**내용**: RESTful API, HTTP 상태 코드, WebFlux 반환 타입

**사용 시기**:
- 새로운 API 엔드포인트 설계 시
- HTTP 상태 코드 결정 시
- Mono<ResponseEntity<T>> 패턴 확인 시

**토큰**: ~2,800

### 5. database-query.md (데이터베이스)

**내용**: JOOQ 쿼리 규칙, SELECT asterisk 금지, Soft Delete

**사용 시기**:
- 데이터베이스 쿼리 작성 시
- Repository 구현 시
- Audit Trail 적용 시

**토큰**: ~2,500

### 6. code-style.md (코드 스타일)

**내용**: 로깅, 네이밍, Kotlin 특성, WebFlux 패턴

**사용 시기**:
- println, 이모티콘, FQCN 금지 확인 시
- 네이밍 규칙 확인 시
- WebFlux Reactive 패턴 작성 시

**토큰**: ~2,200

### 7. reactor-sinks-event.md (Reactor Sinks API 이벤트 패턴)

**내용**: WebFlux 환경 Reactor Sinks API 이벤트 처리, Critical/Non-Critical Path 분리

**사용 시기**:
- WebFlux 환경에서 이벤트 기반 비동기 처리 구현 시
- Critical Path(카운트 등)와 Non-Critical Path(협업 필터링 등) 분리 시
- 메인 리액티브 체인과 독립적인 작업 처리 시

**토큰**: ~3,000

**⚠️ 중요**: WebFlux 환경에서는 ApplicationEventPublisher가 아닌 Reactor Sinks API를 사용해야 합니다!

### 8. reactor-sinks-event-testing.md (Reactor Sinks 이벤트 테스팅)

**내용**: Reactor Sinks 이벤트 테스트 전략, Critical/Non-Critical Path 테스트 구분

**사용 시기**:
- Reactor Sinks 이벤트 기반 기능의 테스트 작성 시
- Critical Path 동기 검증 vs Non-Critical Path 비동기 검증 확인 시
- Awaitility 사용 여부 판단 시

**토큰**: ~2,500

### 9. quick-reference.md (빠른 참조)

**내용**: 체크리스트, 개발 프로세스, 핵심 규칙 요약

**사용 시기**:
- 모든 작업 시작 전
- 빠른 참조가 필요할 때
- 코드 리뷰 전

**토큰**: ~1,800

### 10. git.md (Git Convention)

**내용**: 커밋 메시지 형식, 커밋 단위, PR 규칙, 브랜치 전략

**사용 시기**:
- 커밋 메시지 작성 전
- PR 생성 전
- 브랜치 생성 시
- Git Convention 확인 시

**토큰**: ~3,500

### 11. frontend-api-integration.md (프론트엔드 API 통합) ⚠️ 필수

**내용**: 프론트엔드 개발 시 백엔드 API 스펙 확인 및 타입 일치 가이드

**사용 시기**:
- **프론트엔드 타입 정의 작성 전 (필수)**
- **프론트엔드 API 클라이언트 작성 전 (필수)**
- 백엔드-프론트엔드 API 불일치 디버깅 시
- 새로운 API 엔드포인트 추가 시

**토큰**: ~3,000

**⚠️ 중요**: 프론트엔드 작업 시 백엔드 Controller와 DTO를 **반드시** 먼저 확인해야 합니다!

## 작업별 필수 Skill 매핑

| 작업 유형 | 필수 Skill | 예상 토큰 |
|---------|-----------|---------  |
| **새로운 API 개발** | core-principles → mvc-layers → testing-guide → api-design | ~8,000 |
| **프론트엔드 타입 정의** | **frontend-api-integration (필수)** → api-design | ~5,000 |
| **프론트엔드 API 클라이언트** | **frontend-api-integration (필수)** → api-design | ~5,000 |
| **테스트 작성** | testing-guide → core-principles | ~4,000 |
| **데이터베이스 쿼리** | database-query → core-principles | ~4,000 |
| **코드 리뷰** | quick-reference | ~2,000 |
| **Reactor Sinks 이벤트 처리** | reactor-sinks-event → reactor-sinks-event-testing → mvc-layers | ~8,000 |
| **코드 스타일 수정** | code-style → quick-reference | ~3,000 |
| **리팩토링** | core-principles → mvc-layers → quick-reference | ~6,000 |
| **커밋 작성** | git | ~3,500 |
| **PR 생성** | git | ~3,500 |

## Skill 사용 방법

### 방법 1: 직접 Skill 로드

```
"좋아요 기능을 추가해줘. /skill mvc-layers와 /skill testing-guide를 참고해줘."
```

### 방법 2: 자연스러운 요청 (권장)

Claude가 작업 내용을 분석하여 필요한 Skill을 자동으로 로드합니다.

```
"좋아요 기능을 추가해줘"
→ Claude가 자동으로 mvc-layers, testing-guide, api-design, database-query를 로드
```

### 방법 3: 빠른 참조

간단한 확인이 필요할 때는 quick-reference만 로드합니다.

```
"이 PR 리뷰해줘"
→ quick-reference 로드 (~2,000 토큰)
```

## 실제 사용 시나리오

### 시나리오 1: 새로운 API 개발

```
작업: "좋아요 기능 API 만들어줘"

필요한 Skill:
- core-principles (TDD, SOLID 확인)
- mvc-layers (계층 구조 확인)
- testing-guide (테스트 템플릿 확인)
- api-design (REST API 규칙 확인)

토큰 사용량: 약 8,000 토큰
기존 CLAUDE.md: 약 40,000 토큰
절감 효과: 80% ↓
```

### 시나리오 2: 코드 리뷰

```
작업: "이 PR 리뷰해줘"

필요한 Skill:
- quick-reference (체크리스트 확인)

토큰 사용량: 약 2,000 토큰
기존 CLAUDE.md: 약 40,000 토큰
절감 효과: 95% ↓
```

### 시나리오 3: 데이터베이스 쿼리 작성

```
작업: "유저별 콘텐츠 조회 쿼리 작성해줘"

필요한 Skill:
- database-query (JOOQ 규칙, asterisk 금지)
- core-principles (Audit Trail 확인)

토큰 사용량: 약 4,000 토큰
기존 CLAUDE.md: 약 40,000 토큰
절감 효과: 90% ↓
```

### 시나리오 4: 복잡한 기능 (Reactor Sinks Event 기반)

```
작업: "좋아요 기능에 Reactor Sinks로 추천 시스템 연동해줘"

필요한 Skill:
- mvc-layers (계층 구조)
- reactor-sinks-event (이벤트 패턴)
- reactor-sinks-event-testing (이벤트 테스트)
- database-query (쿼리 규칙)

토큰 사용량: 약 11,000 토큰
기존 CLAUDE.md: 약 40,000 토큰
절감 효과: 72% ↓
```

## 구현 시 고려사항

1. **Skill 호출 명시**: Claude가 어떤 Skill을 로드했는지 사용자에게 명시
2. **로드 순서**: 의존성이 있는 Skill은 순서대로 로드 (예: core-principles → mvc-layers)
3. **중복 방지**: 이미 로드한 Skill은 다시 로드하지 않음
4. **README.md 우선 참조**: Claude가 작업 시작 전 README.md를 먼저 읽고 필요한 Skill 판단
5. **토큰 사용량 모니터링**: 각 Skill의 토큰 사용량을 측정하여 500줄 이하 유지

## 기대 효과

- **평균 토큰 절감**: 75-95% (작업 유형에 따라 다름)
- **빠른 응답**: 필요한 규칙만 로드하여 컨텍스트 이해 시간 단축
- **명확한 규칙 적용**: 작업과 관련된 규칙만 집중하여 실수 방지
- **유지보수 용이**: 특정 규칙 수정 시 해당 Skill만 업데이트

## 기존 CLAUDE.md와의 관계

- **CLAUDE.md**: Deprecated, 더 이상 업데이트하지 않음
- **Skills**: 현재 활발히 사용 중, 지속적으로 업데이트
- **마이그레이션**: 모든 내용이 Skills로 완전히 이전됨

## 정리

**모든 개발 작업 시 이 README를 먼저 읽고 필요한 Skill을 선택하세요!**

1. **작업별 필수 Skill 매핑 표** 확인
2. **필요한 Skill만 선택적으로 로드**
3. **토큰 사용량 75-95% 절감**
4. **빠른 응답과 명확한 규칙 적용**
