# Git Convention Guide

## 개요

GrowSnap 프론트엔드 프로젝트의 Git 커밋 및 PR 규칙을 정의합니다.
Conventional Commits 스타일을 따르며, 명확한 변경 이력 추적을 목표로 합니다.

---

## 커밋 메시지 형식

### 기본 구조

```
<type>(<scope>): <subject>

<body> (optional)

<footer> (optional)
```

### Type 분류

| Type | 설명 | 예시 |
|------|------|------|
| **feat** | 기능 추가 | `feat(auth): 사용자 인증 기능 추가` |
| **fix** | 버그 수정 | `fix(like): 좋아요 중복 처리 문제 해결` |
| **refactor** | 리팩토링 (기능 변경 없음) | `refactor(user): UserService 책임 분리` |
| **docs** | 문서 수정/추가 | `docs(api): API 명세서 업데이트` |
| **test** | 테스트 추가/수정 | `test(post): PostController 통합 테스트 추가` |
| **chore** | 빌드/설정 변경 | `chore(deps): 의존성 업데이트` |
| **perf** | 성능 개선 | `perf(post): 게시글 조회 쿼리 최적화` |
| **style** | 코드 스타일 변경 (포맷팅 등) | `style: Prettier 적용` |

### 커밋 메시지 작성 규칙

1. **제목은 50자 이내, 본문은 72자 이내**
2. **마침표 `.` 사용 금지**
3. **명령형 현재 시제**: "추가", "수정", "구현"
4. **본문 상세 설명** (선택):
   - 무엇을 왜 변경했는지 설명
   - 어떻게는 코드로 설명되므로 생략 가능
5. **이슈 연결**: `Closes #번호` 추가

### 커밋 예시

```
feat(profile): 사용자 프로필 조회 화면 구현

ProfileScreen 컴포넌트 생성
프로필 이미지, 닉네임, 자기소개 표시

Closes #42
```

```
fix(stylesheet): New Architecture 호환성 개선

React Native New Architecture에서 StyleSheet 초기화 에러 해결
lazy evaluation 적용

Fixes #58
```

---

## 커밋 단위 (Granularity)

### 프론트엔드 개발 워크플로우에 따른 커밋 시점

```
1. 이슈 확인 및 브랜치 생성
   ↓
2. 화면/컴포넌트 UI 구현
   ↓
3. 상태 관리 및 비즈니스 로직 구현
   ↓
4. API 연동 (백엔드 API 호출)
   ↓
5. 빌드 및 테스트 (에뮬레이터/디바이스에서 동작 확인)
   ↓
6. 커밋 (feat(scope): message)  ← 여기서 커밋!
```

### 커밋 단위 원칙

1. **하나의 논리적 변경 = 하나의 커밋**
   - ✅ GOOD: `feat 게시글 좋아요 기능 추가` (UI + 상태 관리 + API 연동)
   - ❌ BAD: 여러 기능을 한 커밋에 섞음

2. **빌드가 성공하고 앱이 정상 동작하는 단위로 커밋**
   - 빌드 성공 + 에뮬레이터 실행 확인 후 커밋
   - 일부 화면 깨짐이나 에러는 용인하지 않음

3. **문서화와 함께 커밋**
   - 주요 컴포넌트에 JSDoc 주석 작성 완료 후 커밋
   - 문서 누락 시 별도 `[Docs]` 커밋으로 추가

4. **독립적으로 리뷰 가능한 크기**
   - 한 커밋이 500줄을 초과하지 않도록 권장
   - 대규모 리팩토링은 여러 단계로 분할

### 커밋 단위 예시

**✅ GOOD: 적절한 커밋 단위**
```
Commit 1: feat: 댓글 작성 UI 구현
Commit 2: feat: 댓글 수정/삭제 기능 구현
Commit 3: feat: 댓글 API 연동
Commit 4: test: 댓글 컴포넌트 단위 테스트 추가
Commit 5: docs: 댓글 컴포넌트 사용법 문서 작성
```

**❌ BAD: 너무 작은 단위**
```
Commit 1: UI 레이아웃 작성
Commit 2: 스타일 추가
Commit 3: 상태 관리 추가
Commit 4: API 호출 함수 작성
Commit 5: 에러 핸들링 추가  ← 각 단계를 분리하면 리뷰가 어려움
```

**❌ BAD: 너무 큰 단위**
```
Commit 1: feat 사용자 관리 시스템 전체 구현
- 로그인, 로그아웃, 프로필 조회, 프로필 수정, 팔로우...
← 한 커밋에 너무 많은 기능이 섞임
```

---

## Pull Request (PR) 규칙

### PR 제목 형식

```
[Type] 간단한 설명
```

예시:
- `feat Google OAuth 로그인 구현`
- `[Fix] StyleSheet New Architecture 호환성 개선`

### PR 본문 템플릿

```markdown
## 개요
이 PR이 해결하는 문제와 주요 변경 사항을 1-2문장으로 요약

## 변경 사항
- 추가된 기능/수정된 버그 목록 (불릿 포인트)
- 새로운 화면/컴포넌트
- API 연동 내용

## 테스트
- [ ] 빌드 성공 확인
- [ ] 에뮬레이터/디바이스에서 동작 확인
- [ ] LogCat 에러 없음 확인
- [ ] 수동 테스트 완료 (모든 UI 인터랙션)

## 스크린샷/영상 (선택)
앱 화면 캡처, 동작 영상 등

## 관련 이슈
Closes #이슈번호
```

### 이슈 연결 키워드

| 키워드 | 사용 시기 |
|--------|----------|
| `Closes #이슈번호` | PR이 머지되면 이슈가 자동으로 닫힘 |
| `Fixes #이슈번호` | 버그 수정 시 사용 (자동 닫힘) |
| `Relates to #이슈번호` | 관련 이슈지만 자동으로 닫히지 않음 |

### PR 작성 시 주의사항

1. **Draft PR 활용**: 작업 중인 PR은 Draft로 생성
2. **리뷰어 지정**: 최소 1명 이상의 리뷰어 지정
3. **Self Review**: PR 생성 후 본인이 먼저 코드 리뷰
4. **충돌 해결**: 머지 전 최신 main 브랜치와 충돌 해결
5. **CI 통과**: 모든 CI 테스트가 통과해야 머지 가능

---

## 브랜치 전략

### 브랜치 네이밍

```
feature/ISSUE-번호-간단한-설명
fix/ISSUE-번호-간단한-설명
docs/ISSUE-번호-간단한-설명
refactor/ISSUE-번호-간단한-설명
```

예시:
- `feature/ISSUE-42-user-profile-screen`
- `fix/ISSUE-58-stylesheet-new-arch-compat`
- `docs/ISSUE-65-component-usage-guide`

### 브랜치 생명주기

1. **이슈 생성**: GitHub Issue로 작업 정의
2. **브랜치 생성**: `feature/ISSUE-번호` 형식으로 생성
3. **커밋**: 작업 완료 후 규칙에 맞춰 커밋
4. **PR 생성**: Draft 또는 정식 PR로 생성
5. **리뷰**: 코드 리뷰 진행
6. **머지**: Squash and Merge 또는 Rebase and Merge
7. **브랜치 삭제**: 머지 후 자동 삭제

---

## Git 명령어 팁

### 커밋 수정

```bash
# 마지막 커밋 메시지 수정
git commit --amend

# 마지막 커밋에 파일 추가
git add .
git commit --amend --no-edit
```

### 브랜치 최신화

```bash
# main 브랜치 최신 내용 가져오기
git fetch origin main

# 현재 브랜치에 main 변경사항 반영 (Rebase)
git rebase origin/main

# 충돌 해결 후
git add .
git rebase --continue
```

### 커밋 정리 (Interactive Rebase)

```bash
# 최근 3개 커밋 정리
git rebase -i HEAD~3

# 사용 가능한 명령어:
# pick: 커밋 유지
# reword: 커밋 메시지 수정
# squash: 이전 커밋과 합침
# drop: 커밋 삭제
```

---

## 체크리스트

### 커밋 전 체크리스트
- [ ] 빌드가 성공하는가? (`npx expo run:android`)
- [ ] 에뮬레이터/디바이스에서 정상 동작하는가?
- [ ] LogCat에 에러가 없는가?
- [ ] 커밋 메시지 형식이 올바른가? (`[Type] 설명`)
- [ ] JSDoc 주석이 작성되었는가? (주요 컴포넌트)

### PR 생성 전 체크리스트
- [ ] Self Review를 완료했는가?
- [ ] 모든 커밋이 의미 있는 단위로 분리되었는가?
- [ ] PR 본문 템플릿을 작성했는가?
- [ ] 스크린샷/영상을 첨부했는가? (UI 변경 시)
- [ ] 관련 이슈를 연결했는가? (`Closes #번호`)
- [ ] CI가 통과하는가?

---

## 참고 자료

- **Conventional Commits**: https://www.conventionalcommits.org/
- **GitHub PR Best Practices**: https://docs.github.com/en/pull-requests/collaborating-with-pull-requests
- **Effective Git Commit Messages**: https://cbea.ms/git-commit/
