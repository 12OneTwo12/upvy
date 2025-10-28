# Git Convention Guide

## 개요

GrowSnap 백엔드 프로젝트의 Git 커밋 및 PR 규칙을 정의합니다.
Conventional Commits 스타일을 따르며, 명확한 변경 이력 추적을 목표로 합니다.

---

## 커밋 메시지 형식

### 기본 구조

```
[Type] 간단한 설명

상세 설명 (선택사항)

Closes #이슈번호
```

### Type 분류

| Type | 사용 시기 | 예시 |
|------|----------|------|
| **[Feat]** | 새로운 기능 추가 | `[Feat] Google OAuth 로그인 구현` |
| **[Fix]** | 버그 수정 | `[Fix] 좋아요 중복 처리 문제 해결` |
| **[Refactor]** | 코드 리팩토링 (기능 변경 없음) | `[Refactor] UserService 책임 분리` |
| **[Docs]** | 문서 수정/추가 | `[Docs] API 명세서 업데이트` |
| **[Test]** | 테스트 추가/수정 | `[Test] PostController 통합 테스트 추가` |
| **[Chore]** | 빌드/설정 변경 | `[Chore] Gradle 의존성 업데이트` |
| **[Perf]** | 성능 개선 | `[Perf] 게시글 조회 쿼리 최적화` |
| **[Style]** | 코드 스타일 변경 (포맷팅 등) | `[Style] Ktlint 적용` |

### 커밋 메시지 작성 규칙

1. **한글 사용**: 제목과 본문 모두 한글로 작성
2. **명령형 현재 시제**: "추가", "수정", "구현" (X: "추가했음", "수정됨")
3. **간결한 제목**: 50자 이내로 요약
4. **본문 상세 설명** (선택):
   - 무엇을 왜 변경했는지 설명
   - 어떻게는 코드로 설명되므로 생략 가능
5. **이슈 연결**: 관련 이슈가 있으면 하단에 `Closes #번호` 추가

### 커밋 예시

```
[Feat] 사용자 프로필 조회 API 구현

- GET /api/v1/users/{userId}/profile 엔드포인트 추가
- UserProfileResponse DTO 생성
- 팔로워/팔로잉 수 집계 로직 추가

Closes #42
```

```
[Fix] Soft Delete 적용 시 deletedAt NULL 체크 누락 해결

PostRepository의 findAll() 메서드에서 deletedAt IS NULL 조건이
누락되어 삭제된 게시글이 조회되던 문제 수정.

Fixes #58
```

---

## 커밋 단위 (Granularity)

### TDD 워크플로우에 따른 커밋 시점

```
1. 이슈 확인 및 브랜치 생성
   ↓
2. Controller 작성 (API 스펙 정의)
   ↓
3. Service/Repository 구현
   ↓
4. KDoc + REST Docs + Asciidoc 작성
   ↓
5. 빌드 및 테스트 (모두 정상이여야함, 일부 실패 용인하지 않음)
   ↓
6. 커밋 (feat(scope): message)  ← 여기서 커밋!
```

### 커밋 단위 원칙

1. **하나의 논리적 변경 = 하나의 커밋**
   - ✅ GOOD: `[Feat] 게시글 좋아요 기능 추가` (Controller + Service + Repository + Tests)
   - ❌ BAD: 여러 기능을 한 커밋에 섞음

2. **테스트가 통과하는 단위로 커밋**
   - 모든 빌드와 테스트가 성공해야만 커밋 가능
   - 일부 테스트 실패는 용인하지 않음

3. **문서화와 함께 커밋**
   - KDoc, REST Docs, Asciidoc 작성 완료 후 커밋
   - 문서 누락 시 별도 `[Docs]` 커밋으로 추가

4. **독립적으로 리뷰 가능한 크기**
   - 한 커밋이 500줄을 초과하지 않도록 권장
   - 대규모 리팩토링은 여러 단계로 분할

### 커밋 단위 예시

**✅ GOOD: 적절한 커밋 단위**
```
Commit 1: [Feat] 댓글 생성 API 구현
Commit 2: [Feat] 댓글 수정 API 구현
Commit 3: [Feat] 댓글 삭제 API 구현
Commit 4: [Test] 댓글 API 통합 테스트 추가
Commit 5: [Docs] 댓글 API 명세서 작성
```

**❌ BAD: 너무 작은 단위**
```
Commit 1: Controller 작성
Commit 2: Service 작성
Commit 3: Repository 작성
Commit 4: DTO 작성
Commit 5: 테스트 작성  ← 각 레이어를 분리하면 리뷰가 어려움
```

**❌ BAD: 너무 큰 단위**
```
Commit 1: [Feat] 사용자 관리 시스템 전체 구현
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
- `[Feat] Google OAuth 로그인 구현`
- `[Fix] 게시글 조회 시 Soft Delete 미적용 문제 해결`

### PR 본문 템플릿

```markdown
## 개요
이 PR이 해결하는 문제와 주요 변경 사항을 1-2문장으로 요약

## 변경 사항
- 추가된 기능/수정된 버그 목록 (불릿 포인트)
- API 엔드포인트 변경 사항
- DB 스키마 변경 사항

## 테스트
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 수동 테스트 완료 (Postman/HTTP Client)

## 스크린샷/로그 (선택)
API 응답 예시, 로그 출력 등

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
- `feature/ISSUE-42-user-profile-api`
- `fix/ISSUE-58-soft-delete-null-check`
- `docs/ISSUE-65-api-spec-update`

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
- [ ] 빌드가 성공하는가? (`./gradlew build`)
- [ ] 모든 테스트가 통과하는가? (`./gradlew test`)
- [ ] 커밋 메시지 형식이 올바른가? (`[Type] 설명`)
- [ ] KDoc과 REST Docs가 작성되었는가?

### PR 생성 전 체크리스트
- [ ] Self Review를 완료했는가?
- [ ] 모든 커밋이 의미 있는 단위로 분리되었는가?
- [ ] PR 본문 템플릿을 작성했는가?
- [ ] 관련 이슈를 연결했는가? (`Closes #번호`)
- [ ] CI가 통과하는가?

---

## 참고 자료

- **Conventional Commits**: https://www.conventionalcommits.org/
- **GitHub PR Best Practices**: https://docs.github.com/en/pull-requests/collaborating-with-pull-requests
- **Effective Git Commit Messages**: https://cbea.ms/git-commit/
