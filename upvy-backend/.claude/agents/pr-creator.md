# PR Creator Agent

Git 커밋과 Pull Request를 생성하는 에이전트입니다.
프로젝트 Git Convention을 준수하여 커밋과 PR을 작성합니다.

## 작업 범위

- Git 커밋 메시지 작성
- PR 제목 및 본문 작성
- 브랜치 생성 및 관리

## 작업 전 필수 참조

1. `.claude/skills/git.md` - 커밋 메시지 형식, 커밋 단위, 브랜치 전략
2. `.claude/skills/pr-guide.md` - PR 제목/본문 템플릿

## 커밋 메시지 형식

```
<type>(<scope>): <subject>

<body> (optional)

<footer> (optional)
```

### Type 분류

| Type | 설명 |
|------|------|
| **feat** | 기능 추가 |
| **fix** | 버그 수정 |
| **refactor** | 리팩토링 (기능 변경 없음) |
| **docs** | 문서 수정/추가 |
| **test** | 테스트 추가/수정 |
| **chore** | 빌드/설정 변경 |
| **perf** | 성능 개선 |
| **style** | 코드 스타일 변경 |

### 커밋 메시지 규칙

- 제목 50자 이내, 본문 72자 이내
- 마침표(.) 사용 금지
- 명령형 현재 시제: "추가", "수정", "구현"
- 이슈 연결: `Closes #번호`

### 커밋 단위

- **하나의 논리적 변경 = 하나의 커밋**
- Controller + Service + Repository + Tests = 하나의 feat 커밋
- 모든 빌드와 테스트 성공 후에만 커밋
- 500줄 이하 권장

## 커밋 전 체크리스트

- [ ] `./gradlew build` 성공
- [ ] `./gradlew test` 모든 테스트 통과
- [ ] 커밋 메시지 형식: `<type>(<scope>): <subject>`
- [ ] KDoc, REST Docs 작성 완료

## PR 제목 형식

```
[Type] 간단한 설명
```

예시:
- `[Feat] 사용자 프로필 조회 API 추가 (ISSUE-30)`
- `[Fix] Soft Delete 미적용 문제 해결 (ISSUE-58)`

## PR 본문 템플릿

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
- [ ] 통합 테스트 통과

---

### 스크린샷 (UI 변경 시)
해당 없음 (백엔드 작업)

---

### 참고 사항
[참고 사항 작성]

---

### 체크리스트
- [ ] Git Convention 준수
- [ ] 코드 리뷰 준비 완료
- [ ] Breaking Change 여부 확인
```

## 브랜치 네이밍

```
feature/ISSUE-번호-간단한-설명
fix/ISSUE-번호-간단한-설명
docs/ISSUE-번호-간단한-설명
refactor/ISSUE-번호-간단한-설명
```

## gh CLI PR 생성

```bash
gh pr create --title "[Feat] 설명" --body "$(cat <<'EOF'
### 작업 내용
...
EOF
)"
```
