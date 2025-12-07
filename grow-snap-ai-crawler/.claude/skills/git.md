# Git Convention Guide

## 개요

GrowSnap AI Crawler 프로젝트의 Git 커밋 및 PR 규칙을 정의합니다.
Conventional Commits 스타일을 따릅니다.

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
| **feat** | 기능 추가 | `feat(crawl): YouTube CC 비디오 크롤링 기능 추가` |
| **fix** | 버그 수정 | `fix(transcribe): Whisper API 타임아웃 문제 해결` |
| **refactor** | 리팩토링 (기능 변경 없음) | `refactor(ai): LlmClient 인터페이스 분리` |
| **docs** | 문서 수정/추가 | `docs(readme): 설치 가이드 업데이트` |
| **test** | 테스트 추가/수정 | `test(analyze): AnalyzeProcessor 단위 테스트 추가` |
| **chore** | 빌드/설정 변경 | `chore(deps): Spring Batch 버전 업데이트` |
| **perf** | 성능 개선 | `perf(edit): FFmpeg 인코딩 최적화` |

### Scope 목록 (이 프로젝트용)

| Scope | 설명 |
|-------|------|
| `crawl` | YouTube 크롤링 관련 |
| `transcribe` | 음성-텍스트 변환 관련 |
| `analyze` | LLM 분석 관련 |
| `edit` | FFmpeg 비디오 편집 관련 |
| `review` | 품질 검수 관련 |
| `ai` | AI 클라이언트 공통 |
| `batch` | Spring Batch 공통 |
| `config` | 설정 관련 |
| `deps` | 의존성 관련 |

### 커밋 예시

```
feat(crawl): YouTube CC 비디오 크롤링 기능 구현

- YouTubeCcVideoReader 구현
- VideoDownloadProcessor 구현
- yt-dlp 래퍼 클래스 추가
- S3 업로드 연동

Closes #14
```

```
fix(ai): Vertex AI Rate Limit 처리 개선

지수 백오프를 적용하여 Rate Limit 오류 시 재시도 간격 증가
최대 3회 재시도 후 실패 처리

Fixes #25
```

## 커밋 단위

### 적절한 커밋 단위

```
Commit 1: feat(crawl): YouTube Data API 클라이언트 구현
Commit 2: feat(crawl): yt-dlp 래퍼 구현
Commit 3: feat(crawl): CrawlStep 구현
Commit 4: test(crawl): CrawlStep 단위 테스트 추가
```

### 너무 작은 단위 (피해야 함)

```
Commit 1: ItemReader 인터페이스 정의
Commit 2: ItemReader 구현 시작
Commit 3: ItemReader 구현 완료
Commit 4: ItemReader 테스트 추가
```

### 너무 큰 단위 (피해야 함)

```
Commit 1: AI 콘텐츠 생성 파이프라인 전체 구현
(CrawlStep + TranscribeStep + AnalyzeStep + EditStep 모두 포함)
```

## 브랜치 전략

### 브랜치 네이밍

```
feature/ISSUE-번호-간단한-설명
fix/ISSUE-번호-간단한-설명
docs/ISSUE-번호-간단한-설명
refactor/ISSUE-번호-간단한-설명
```

예시:
- `feature/ISSUE-14-ai-content-crawler`
- `fix/ISSUE-25-rate-limit-handling`
- `docs/ISSUE-30-api-docs-update`

### 브랜치 생명주기

1. **이슈 생성**: GitHub Issue로 작업 정의
2. **브랜치 생성**: `feature/ISSUE-번호` 형식으로 생성
3. **커밋**: 작업 완료 후 규칙에 맞춰 커밋
4. **PR 생성**: Draft 또는 정식 PR로 생성
5. **리뷰**: 코드 리뷰 진행
6. **머지**: Squash and Merge
7. **브랜치 삭제**: 머지 후 자동 삭제

## Pull Request 규칙

### PR 제목 형식

```
[Type] 간단한 설명
```

예시:
- `[Feat] AI 콘텐츠 크롤링 파이프라인 구현`
- `[Fix] Whisper API 타임아웃 처리 개선`

### PR 본문 템플릿

```markdown
## 개요
이 PR이 해결하는 문제와 주요 변경 사항을 1-2문장으로 요약

## 변경 사항
- 추가된 기능/수정된 버그 목록 (불릿 포인트)
- Step 추가/변경 내용
- AI 클라이언트 변경 내용

## 테스트
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 배치 Job 수동 실행 테스트

## 관련 이슈
Closes #이슈번호
```

## 체크리스트

### 커밋 전 체크리스트

- [ ] 빌드가 성공하는가? (`./gradlew build`)
- [ ] 모든 테스트가 통과하는가? (`./gradlew test`)
- [ ] 커밋 메시지 형식이 올바른가? (`<type>(<scope>): <subject>`)
- [ ] KDoc이 작성되었는가?

### PR 생성 전 체크리스트

- [ ] Self Review를 완료했는가?
- [ ] 모든 커밋이 의미 있는 단위로 분리되었는가?
- [ ] PR 본문 템플릿을 작성했는가?
- [ ] 관련 이슈를 연결했는가? (`Closes #번호`)
- [ ] CI가 통과하는가?

## Git 명령어 팁

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

### 커밋 수정

```bash
# 마지막 커밋 메시지 수정
git commit --amend

# 마지막 커밋에 파일 추가
git add .
git commit --amend --no-edit
```
