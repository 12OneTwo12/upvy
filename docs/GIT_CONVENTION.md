# Git Convention

> **Version 1.0** | Last Updated: 2025-10-21

Upvy 프로젝트의 Git 사용 규칙입니다. 일관성 있는 커밋 메시지와 브랜치 관리를 위해 모든 팀원이 준수해야 합니다.

---

## 📋 목차

1. [커밋 메시지 규칙](#1-커밋-메시지-규칙)
2. [브랜치 네이밍 규칙](#2-브랜치-네이밍-규칙)
3. [작업 플로우](#3-작업-플로우)
4. [Pull Request 규칙](#4-pull-request-규칙)

---

## 1. 커밋 메시지 규칙

### 1.1 커밋 메시지 구조

```
<type>(<scope>): <subject>

<body> (optional)

<footer> (optional)
```

### 1.2 Type (필수)

커밋의 종류를 나타냅니다.

| Type | 설명 | 예시 |
|------|------|------|
| `feat` | 새로운 기능 추가 | `feat(auth): Google 소셜 로그인 추가` |
| `fix` | 버그 수정 | `fix(feed): 무한 스크롤 오류 수정` |
| `docs` | 문서 수정 | `docs(readme): 설치 가이드 추가` |
| `style` | 코드 포맷팅, 세미콜론 누락 등 (기능 변경 없음) | `style(auth): 코드 포맷팅` |
| `refactor` | 코드 리팩토링 (기능 변경 없음) | `refactor(api): 서비스 레이어 분리` |
| `test` | 테스트 코드 추가/수정 | `test(auth): 로그인 단위 테스트 추가` |
| `chore` | 빌드, 설정 파일 수정 | `chore(deps): Spring Boot 3.2 업데이트` |
| `perf` | 성능 개선 | `perf(feed): 쿼리 최적화` |
| `ci` | CI/CD 설정 변경 | `ci(github): GitHub Actions 워크플로우 추가` |
| `build` | 빌드 시스템 변경 | `build(gradle): 의존성 추가` |
| `revert` | 커밋 되돌리기 | `revert: feat(auth): 로그인 기능 추가` |

### 1.3 Scope (선택)

변경된 범위를 나타냅니다.

**백엔드 예시**: `auth`, `feed`, `user`, `video`, `analytics`, `notification`

**프론트엔드 예시**: `login`, `profile`, `feed`, `upload`, `notification`

### 1.4 Subject (필수)

- **50자 이내**로 작성
- **마침표(.) 사용 금지**
- **명령형**으로 작성 (예: "추가했다" ❌ → "추가" ✅)
- 첫 글자는 **소문자** (영문 기준)

### 1.5 Body (선택)

- **72자 이내**로 줄바꿈
- **무엇을, 왜** 변경했는지 작성
- **어떻게**는 코드로 설명

### 1.6 Footer (선택)

- 이슈 트래커 ID 참조
- `Closes #이슈번호`: 이슈 자동 종료
- `Relates to #이슈번호`: 이슈 연결 (종료 안 함)
- `Fixes #이슈번호`: 버그 수정 이슈 자동 종료

### 1.7 작성 예시

#### 기본 예시
```
feat(auth): 사용자 인증 기능 추가

사용자 로그인 및 인증 기능을 추가했습니다.

Closes #123
```

#### 간단한 예시
```
fix(feed): 비디오 자동재생 오류 수정
```

#### 상세한 예시
```
feat(analytics): 크리에이터 대시보드 추가

크리에이터가 콘텐츠 성과를 확인할 수 있는 대시보드를 추가했습니다.
- 조회수, 좋아요, 댓글 수 표시
- 최근 30일 추이 차트
- 시청자 인구통계

Closes #45
Relates to #50
```

---

## 2. 브랜치 네이밍 규칙

### 2.1 브랜치 유형

| 브랜치 유형 | 형식 | 예시 |
|------------|------|------|
| **기능 개발** | `feature/ISSUE-<번호>` | `feature/ISSUE-13` |
| **버그 수정** | `fix/ISSUE-<번호>` | `fix/ISSUE-42` |
| **릴리즈** | `release/v<버전>` | `release/v1.2.0` |
| **긴급 수정** | `hotfix/ISSUE-<번호>` | `hotfix/ISSUE-14` |

### 2.2 브랜치 네이밍 규칙

- **ISSUE-번호** 또는 **v버전번호**를 명시하여 추적 가능하도록 합니다.
- 소문자와 하이픈(`-`)을 사용합니다.
- 브랜치명만 보고도 무엇을 하는지 알 수 있어야 합니다.

### 2.3 예시

```bash
# 기능 개발
feature/ISSUE-13        # 이슈 #13 기능 개발
feature/ISSUE-25        # 이슈 #25 기능 개발

# 버그 수정
fix/ISSUE-42            # 이슈 #42 버그 수정

# 릴리즈
release/v1.0.0          # 1.0.0 버전 릴리즈
release/v1.2.0          # 1.2.0 버전 릴리즈

# 긴급 수정
hotfix/ISSUE-14         # 이슈 #14 긴급 수정
```

---

## 3. 작업 플로우

### 3.1 기본 플로우

```bash
# 1. 최신 코드 받기
git checkout develop
git pull origin develop

# 2. 새 브랜치 생성
git checkout -b feature/ISSUE-13

# 3. 작업 및 커밋
git add .
git commit -m "feat(auth): Google 로그인 추가"

# 4. 원격 저장소에 푸시
git push origin feature/ISSUE-13

# 5. Pull Request 생성
```

### 3.2 브랜치 전략

- **main**: 프로덕션 배포 브랜치 (항상 배포 가능한 상태)
- **develop**: 개발 통합 브랜치
- **feature/**: 기능 개발 브랜치 (`develop`에서 분기)
- **release/**: 릴리즈 준비 브랜치 (`develop`에서 분기)
- **hotfix/**: 긴급 수정 브랜치 (`main`에서 분기)

### 3.3 머지 규칙

```bash
# feature → develop
feature/ISSUE-13 → develop (squash merge 권장)

# release → main + develop
release/v1.2.0 → main (merge commit)
release/v1.2.0 → develop (merge commit)

# hotfix → main + develop
hotfix/ISSUE-14 → main (merge commit)
hotfix/ISSUE-14 → develop (merge commit)
```

### 3.4 커밋 정리 (Squash)

커밋이 많아질 경우 squash를 활용하여 정리합니다.

```bash
# 최근 3개 커밋을 하나로 합치기
git rebase -i HEAD~3
```

에디터에서 `pick`을 `squash`로 변경하여 커밋을 합칩니다.

---

## 4. Pull Request 규칙

### 4.1 PR 제목

커밋 메시지와 동일한 형식을 사용합니다.

```
[Type] 간단한 설명
```

**예시**:
```
[Feat] Google 소셜 로그인 구현
[Fix] 무한 스크롤 오류 수정
[Docs] Git 컨벤션 문서 추가
```

### 4.2 PR 설명

PR 템플릿에 따라 상세히 작성합니다.

- **무엇을** 구현/수정했는지
- **왜** 필요한지
- **어떻게** 테스트했는지
- **스크린샷** (UI 변경 시)

### 4.3 이슈 연결

PR 본문 또는 커밋 메시지에 다음을 추가하여 이슈를 자동으로 연결합니다.

```
Closes #13
```

이슈 번호를 명시하면 PR이 머지될 때 자동으로 이슈가 종료됩니다.

### 4.4 리뷰 규칙

- 최소 1명 이상의 승인 필요
- 모든 대화는 해결(resolve)되어야 함
- CI 테스트가 통과해야 함

---

## 5. 커밋 단위 가이드

### 5.1 좋은 커밋의 기준

✅ **좋은 커밋**:
- **하나의 논리적 변경만 포함**: 한 가지 목적만 달성
- **독립적으로 빌드 가능**: 커밋 단계마다 프로젝트가 빌드되고 테스트가 통과
- **원자적(Atomic)**: 더 이상 나눌 수 없는 최소 단위
- **의미 있는 메시지**: 커밋 메시지만 보고 변경 내용 파악 가능

### 5.2 커밋 단위 예시

#### ✅ 올바른 커밋 단위

**예시 1: 기능 추가**
```bash
# 좋은 예: 각 단계를 별도 커밋
git commit -m "feat(auth): Google OAuth 설정 추가"
git commit -m "feat(auth): Google 로그인 API 엔드포인트 구현"
git commit -m "test(auth): Google 로그인 테스트 추가"
git commit -m "docs(auth): Google 로그인 API 문서 작성"
```

**예시 2: 버그 수정**
```bash
# 좋은 예: 버그 수정과 테스트를 하나의 커밋으로
git commit -m "fix(feed): 무한 스크롤 중복 로드 오류 수정

- IntersectionObserver 중복 호출 방지
- 로딩 상태 관리 개선
- 엣지 케이스 테스트 추가

Fixes #42"
```

**예시 3: 리팩토링**
```bash
# 좋은 예: 리팩토링과 테스트 수정을 하나의 커밋으로
git commit -m "refactor(api): UserService 레이어 분리

- UserServiceImpl을 UserService와 UserProfileService로 분리
- 단일 책임 원칙(SRP) 적용
- 관련 테스트 수정"
```

#### ❌ 잘못된 커밋 단위

**예시 1: 너무 큰 커밋**
```bash
# 나쁜 예: 여러 기능을 하나의 커밋에
git commit -m "feat: 로그인, 회원가입, 프로필 수정 기능 추가"

# 좋은 방법: 각 기능을 별도 커밋으로 분리
git commit -m "feat(auth): 로그인 기능 추가"
git commit -m "feat(auth): 회원가입 기능 추가"
git commit -m "feat(profile): 프로필 수정 기능 추가"
```

**예시 2: 너무 작은 커밋**
```bash
# 나쁜 예: 논리적으로 하나의 작업을 불필요하게 분리
git commit -m "feat(auth): User 엔티티 추가"
git commit -m "feat(auth): UserRepository 추가"
git commit -m "feat(auth): UserService 추가"
git commit -m "feat(auth): UserController 추가"

# 좋은 방법: 논리적으로 묶기
git commit -m "feat(auth): 사용자 인증 기능 구현

- User 엔티티, Repository, Service, Controller 추가
- 로그인/로그아웃 API 구현
- 테스트 코드 추가"
```

**예시 3: 관련 없는 변경 혼합**
```bash
# 나쁜 예: 서로 관련 없는 변경사항 혼합
git commit -m "fix: 버그 수정 및 새 기능 추가"

# 좋은 방법: 각각 별도 커밋
git commit -m "fix(feed): 비디오 자동재생 오류 수정"
git commit -m "feat(analytics): 조회수 집계 기능 추가"
```

### 5.3 커밋 단위 판단 기준

다음 질문에 "예"라고 답할 수 있다면 좋은 커밋 단위입니다:

1. **이 커밋만으로 빌드와 테스트가 통과하는가?**
2. **커밋 메시지가 변경 내용을 명확히 설명하는가?**
3. **이 커밋을 되돌려도 다른 기능에 영향이 없는가?**
4. **코드 리뷰어가 이 커밋만 보고 변경 내용을 이해할 수 있는가?**

### 5.4 커밋 시점

#### ✅ 커밋해야 할 때
- 하나의 논리적 작업이 완료되었을 때
- 테스트가 모두 통과할 때
- 의미 있는 중간 단계에 도달했을 때

#### ❌ 커밋하지 말아야 할 때
- 빌드가 실패할 때
- 테스트가 실패할 때
- 작업이 중간에 끊긴 상태일 때 (WIP 커밋 제외)

### 5.5 WIP (Work In Progress) 커밋

진행 중인 작업을 임시로 커밋해야 할 때:

```bash
# WIP 커밋 (하루 작업 마무리 등)
git commit -m "wip(auth): 로그인 기능 구현 중"

# 작업 완료 후 WIP 커밋들을 정리 (squash)
git rebase -i HEAD~3  # 최근 3개 커밋을 하나로 합치기
git commit -m "feat(auth): 로그인 기능 추가"
```

**주의**: WIP 커밋은 PR에 포함하기 전에 반드시 정리(squash)해야 합니다.

---

## 6. 기타 규칙

### 6.1 금지 사항

❌ **절대 하지 말아야 할 것들**:
- `main` 브랜치에 직접 커밋
- 대용량 파일 커밋 (비디오, 이미지 등은 S3 사용)
- 민감한 정보 커밋 (API 키, 비밀번호 등)
- 의미 없는 커밋 메시지 (예: "수정", "테스트", "asdf")

### 6.2 권장 사항

✅ **권장하는 것들**:
- 작고 의미 있는 커밋 단위
- 하나의 커밋에 하나의 목적
- 커밋 전 코드 리뷰
- 브랜치는 최신 상태로 유지

---

## 7. 참고 자료

- [Conventional Commits](https://www.conventionalcommits.org/)
- [Git Flow](https://nvie.com/posts/a-successful-git-branching-model/)
- [GitHub Flow](https://guides.github.com/introduction/flow/)

---

## 8. 변경 이력

| 버전 | 날짜 | 변경 사항 |
|------|------|-----------|
| 1.0 | 2025-10-21 | 초안 작성 |

---

**이 컨벤션을 준수하여 깔끔하고 일관성 있는 Git 히스토리를 만들어갑시다!** 🚀
