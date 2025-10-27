# GrowSnap Frontend Pull Request 작성 가이드

> PR 제목과 본문을 템플릿에 맞춰 작성하는 방법을 안내합니다.

## PR 제목 형식

Git Convention의 커밋 메시지 형식과 동일하게 작성합니다:

```
[Type] 간단한 설명
```

### Type 종류

| Type | 설명 | 예시 |
|------|------|------|
| `[Feat]` | 새로운 기능 추가 | `[Feat] Google 소셜 로그인 구현` |
| `[Fix]` | 버그 수정 | `[Fix] 무한 스크롤 오류 수정` |
| `[Docs]` | 문서 수정 | `[Docs] Git 컨벤션 문서 추가` |
| `[Refactor]` | 코드 리팩토링 | `[Refactor] UserService 레이어 분리` |
| `[Test]` | 테스트 코드 추가/수정 | `[Test] 로그인 단위 테스트 추가` |
| `[Chore]` | 빌드, 설정 파일 수정 | `[Chore] Spring Boot 3.2 업데이트` |
| `[Perf]` | 성능 개선 | `[Perf] 쿼리 최적화` |
| `[Style]` | 코드 포맷팅 | `[Style] 코드 포맷팅` |

### PR 제목 예시

```
[Feat] 사용자 프로필 조회 API 추가 (ISSUE-30)
[Fix] 비디오 자동재생 오류 수정 (ISSUE-27)
[Docs] CLAUDE.md를 Claude Code Skills로 분리하여 토큰 효율성 개선 (ISSUE-39)
[Refactor] Service 레이어 SOLID 원칙 적용 (ISSUE-25)
[Test] Controller 테스트 템플릿 적용 (ISSUE-22)
```

## PR 본문 템플릿

프로젝트 루트의 `.github/pull_request_template.md`를 사용합니다.

### 기본 구조

```markdown
### 작업 내용
[간단한 설명]

---

### 관련 이슈
Closes #이슈번호

---

### 작업 사항
- [변경된 항목 요약]
- [추가한 항목 요약]

---

### 테스트
- [ ] 로컬 테스트 완료
- [ ] 단위 테스트 작성/통과
- [ ] 통합 테스트 통과 (해당 시)

---

### 스크린샷 (UI 변경 시)
[스크린샷 추가]

---

### 참고 사항
[참고 사항 작성]

---

### 체크리스트
- [ ] [Git Convention](../docs/GIT_CONVENTION.md) 준수
- [ ] 코드 리뷰 준비 완료
- [ ] Breaking Change 여부 확인
```

## 작성 가이드

### 1. 작업 내용

**무엇을, 왜** 변경했는지 1-3문장으로 간단히 설명합니다.

```markdown
### 작업 내용
사용자 프로필 조회 API를 추가했습니다. 크리에이터의 프로필 정보와 콘텐츠 통계를 확인할 수 있도록 개선했습니다.
```

### 2. 관련 이슈

이슈 번호를 명시하여 자동으로 이슈를 연결합니다.

```markdown
### 관련 이슈
Closes #42
```

**이슈 연결 키워드:**
- `Closes #이슈번호`: PR 머지 시 이슈 자동 종료
- `Fixes #이슈번호`: 버그 수정 이슈 자동 종료
- `Relates to #이슈번호`: 이슈 연결만 (종료 안 함)

### 3. 작업 사항

변경된 내용을 구체적으로 나열합니다.

```markdown
### 작업 사항
- **API 엔드포인트 추가**
  - `GET /api/v1/users/{userId}/profile`: 프로필 조회
  - `PUT /api/v1/users/{userId}/profile`: 프로필 수정

- **테스트 코드 작성**
  - UserProfileController 테스트
  - UserProfileService 테스트
  - UserProfileRepository 통합 테스트

- **문서화**
  - REST Docs 작성
  - KDoc 추가
```

### 4. 테스트

체크리스트 형식으로 테스트 완료 여부를 표시합니다.

```markdown
### 테스트
- [x] 로컬 테스트 완료
- [x] 단위 테스트 작성/통과
- [x] 통합 테스트 통과
```

### 5. 스크린샷 (UI 변경 시)

UI 변경이 있는 경우 스크린샷을 첨부합니다.

```markdown
### 스크린샷 (UI 변경 시)
![프로필 페이지](https://example.com/screenshot.png)
```

UI 변경이 없는 경우:
```markdown
### 스크린샷 (UI 변경 시)
해당 없음 (UI 변경 없음)
```

### 6. 참고 사항

추가 설명이 필요한 경우 작성합니다.

```markdown
### 참고 사항

#### 주요 변경사항
- UserProfile 엔티티에 `bio`, `website` 필드 추가
- Soft Delete 적용
- Audit Trail 필드 포함

#### 성능 최적화
- 프로필 조회 시 N+1 문제 해결
- 인덱스 추가: `user_profiles(user_id, deleted_at)`
```

### 7. 체크리스트

PR 전 필수 확인 사항을 체크합니다.

```markdown
### 체크리스트
- [x] [Git Convention](../docs/GIT_CONVENTION.md) 준수
- [x] 코드 리뷰 준비 완료
- [x] Breaking Change 없음
```

**Breaking Change가 있는 경우:**
```markdown
### 체크리스트
- [x] [Git Convention](../docs/GIT_CONVENTION.md) 준수
- [x] 코드 리뷰 준비 완료
- [x] Breaking Change 있음 (마이그레이션 가이드 작성 완료)
```

## PR 작성 실전 예시

### 예시 1: 기능 추가

```markdown
[Feat] 사용자 프로필 조회 API 추가

### 작업 내용
사용자 프로필 조회 API를 추가했습니다. 크리에이터의 프로필 정보와 콘텐츠 통계를 확인할 수 있도록 개선했습니다.

---

### 관련 이슈
Closes #42

---

### 작업 사항
- **API 엔드포인트 추가**
  - `GET /api/v1/users/{userId}/profile`: 프로필 조회
  - UserProfile 엔티티, Repository, Service, Controller 구현

- **테스트 코드 작성**
  - Component 테스트 (React Testing Library)
  - Hook 테스트 (renderHook)
  - 통합 테스트 (Jest)

- **문서화**
  - TypeScript 타입 정의
  - JSDoc 주석 추가

---

### 테스트
- [x] 로컬 테스트 완료
- [x] 단위 테스트 작성/통과 (커버리지 85%)
- [x] 통합 테스트 통과

---

### 스크린샷 (UI 변경 시)
![로그인 화면](https://example.com/screenshot.png)

---

### 참고 사항

#### 주요 변경사항
- 인스타그램 스타일의 깔끔한 UI 디자인 적용
- 반응형 디자인 시스템 구축 (responsive.ts, theme)
- TypeScript 타입 안정성 확보

#### 성능 최적화
- useMemo/useCallback로 불필요한 리렌더링 방지
- FlatList windowSize 최적화
- 이미지 lazy loading 적용

---

### 체크리스트
- [x] [Git Convention](../docs/GIT_CONVENTION.md) 준수
- [x] 코드 리뷰 준비 완료
- [x] Breaking Change 없음
```

### 예시 2: 버그 수정

```markdown
[Fix] 무한 스크롤 중복 로드 오류 수정

### 작업 내용
피드 무한 스크롤 시 콘텐츠가 중복으로 로드되는 오류를 수정했습니다. IntersectionObserver 중복 호출 방지 로직을 추가했습니다.

---

### 관련 이슈
Fixes #38

---

### 작업 사항
- IntersectionObserver 중복 호출 방지
- 로딩 상태 관리 개선 (`isLoading` 플래그 추가)
- 엣지 케이스 테스트 추가

---

### 테스트
- [x] 로컬 테스트 완료
- [x] 단위 테스트 추가 (중복 로드 방지 시나리오)
- [x] 통합 테스트 통과

---

### 스크린샷 (UI 변경 시)
해당 없음

---

### 참고 사항
**재현 방법:**
1. 피드 페이지 진입
2. 빠르게 스크롤
3. 동일한 콘텐츠가 중복으로 표시됨

**수정 내용:**
- IntersectionObserver 콜백에서 `isLoading` 플래그 체크
- 로딩 중일 때는 추가 요청 방지

---

### 체크리스트
- [x] [Git Convention](../docs/GIT_CONVENTION.md) 준수
- [x] 코드 리뷰 준비 완료
- [x] Breaking Change 없음
```

### 예시 3: 문서 작업

```markdown
[Docs] CLAUDE.md를 Claude Code Skills로 분리하여 토큰 효율성 개선

### 작업 내용
CLAUDE.md(2,144줄, 40,000 토큰)를 8개의 독립적인 Skill 파일로 분리하여 토큰 사용량을 75-95% 절감했습니다.

---

### 관련 이슈
Closes #39

---

### 작업 사항
- **8개의 Skill 파일 생성** (`.claude/skills/`)
  - core-principles.md, mvc-layers.md, testing-guide.md
  - api-design.md, database-query.md, code-style.md
  - spring-event.md, quick-reference.md, README.md

- **개발자용 문서 추가**
  - `/docs/BACKEND_DEVELOPMENT_GUIDE.md`

- **CLAUDE.md 간소화**
  - 2,144줄 → 59줄 (97% 감소)

---

### 테스트
- [x] 파일 구조 및 내용 검증
- [x] 토큰 사용량 확인 (75-95% 절감 달성)

---

### 스크린샷 (UI 변경 시)
해당 없음 (문서 작업)

---

### 참고 사항

#### 토큰 절감 효과
| 작업 유형 | 기존 | Skill 사용 | 절감 |
|---------|-----|-----------|------|
| API 개발 | 40,000 | ~8,000 | 80% ↓ |
| 테스트 작성 | 40,000 | ~4,000 | 90% ↓ |

---

### 체크리스트
- [x] [Git Convention](../docs/GIT_CONVENTION.md) 준수
- [x] 코드 리뷰 준비 완료
- [x] Breaking Change 없음
```

## PR 작성 체크리스트

**PR 생성 전 반드시 확인:**

- [ ] **PR 제목**: `[Type] 간단한 설명 (이슈 번호)` 형식
- [ ] **관련 이슈**: `Closes #이슈번호` 명시
- [ ] **작업 사항**: 변경 내용을 구체적으로 나열
- [ ] **테스트**: 모든 테스트 통과 확인
- [ ] **체크리스트**: Git Convention 준수, 코드 리뷰 준비
- [ ] **Breaking Change**: 있는 경우 명시 및 마이그레이션 가이드 작성

## GitHub에서 PR 생성하기

```bash
# 1. 브랜치 푸시 (이미 완료한 경우 생략)
git push origin feature/ISSUE-42

# 2. GitHub에서 PR 생성
# - GitHub 저장소 페이지 이동
# - "Pull requests" 탭 클릭
# - "New pull request" 버튼 클릭
# - base: main (또는 develop), compare: feature/ISSUE-42 선택
# - 템플릿에 맞춰 제목과 본문 작성
# - "Create pull request" 클릭
```

## gh CLI 사용하기

```bash
# gh CLI로 PR 생성 (템플릿 자동 적용)
gh pr create --title "[Feat] 사용자 프로필 조회 API 추가" \
  --body "$(cat <<'EOF'
### 작업 내용
사용자 프로필 조회 API를 추가했습니다.

---

### 관련 이슈
Closes #42

---

### 작업 사항
- API 엔드포인트 추가
- 테스트 코드 작성

---

### 테스트
- [x] 로컬 테스트 완료
- [x] 단위 테스트 작성/통과

---

### 체크리스트
- [x] Git Convention 준수
- [x] 코드 리뷰 준비 완료
- [x] Breaking Change 없음
EOF
)"
```

## 정리

1. **PR 제목**: `[Type] 간단한 설명` 형식으로 작성
2. **PR 본문**: 템플릿에 맞춰 구체적으로 작성
3. **이슈 연결**: `Closes #이슈번호`로 자동 종료
4. **테스트**: 모든 테스트 통과 확인
5. **체크리스트**: Git Convention 준수, 코드 리뷰 준비

좋은 PR은 리뷰어가 변경 내용을 빠르게 이해하고 피드백할 수 있도록 도와줍니다! 🚀
