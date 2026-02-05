# n8n Workflows

## 워크플로우 목록

### content-generator.json
메인 AI 콘텐츠 생성 파이프라인

## Import 방법

1. n8n UI 접속 (http://localhost:5678)
2. **Settings** → **Workflow** → **Import from File**
3. `content-generator.json` 선택
4. **Import** 클릭

## 필수 Credentials 설정

워크플로우 실행 전 다음 Credentials를 설정해야 합니다:

### 1. Google API (Vertex AI, TTS)
- **Type**: Google API
- **Settings**:
  - Service Account JSON 또는 OAuth2

### 2. HTTP Header Auth (Upvy API)
- **Type**: Header Auth
- **Name**: `Authorization`
- **Value**: `Bearer {UPVY_AI_CREATOR_TOKEN}`

### 3. YouTube OAuth2 (선택)
- **Type**: OAuth2 API
- **Settings**:
  - Client ID / Client Secret
  - Scopes: `youtube.upload`

### 4. Slack (선택)
- **Type**: Slack API
- **Settings**:
  - Bot Token 또는 Webhook URL

## 환경 변수

n8n에서 사용하는 환경 변수:

| 변수명 | 설명 | 예시 |
|--------|------|------|
| `GCP_PROJECT_ID` | GCP 프로젝트 ID | `my-project-123` |
| `VERTEX_AI_LOCATION` | Vertex AI 리전 | `asia-northeast3` |
| `GCS_BUCKET` | GCS 버킷명 | `upvy-ai-content` |
| `UPVY_API_URL` | Upvy Backend URL | `https://api.upvy.io` |
| `INSTAGRAM_USER_ID` | Instagram 계정 ID | `1234567890` |

## 워크플로우 구조

```
┌─────────────────┐
│ Schedule EN     │ (5/day: 6,9,12,15,18시)
│ Schedule JA     │ (3/day: 7,13,19시)
│ Schedule KO     │ (3/day: 8,14,20시)
└────────┬────────┘
         ↓
┌─────────────────┐
│ Set Config      │ 카테고리/난이도/jobId 설정
└────────┬────────┘
         ↓
┌─────────────────────────────────────┐
│ 1. Topic Select (Gemini+Grounding) │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ 2. Script + Quiz Gen (Gemini)      │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ 3. Quality Gate (IF: PASS/FAIL)    │
└────────┬────────────────────────────┘
         ↓ (PASS)
┌─────────────────────────────────────┐
│ 4a. Visual Gen │ 4b. Audio Gen     │ (병렬)
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ 5. Compose (FFmpeg)                │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ 6a. Upvy    │ 6c. YouTube │ 6d. IG │ (병렬)
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Slack - Success/Failure            │
└─────────────────────────────────────┘
```

## 커스터마이징

### 스케줄 변경
각 언어별 Schedule Trigger 노드에서 시간 변경 가능

### 카테고리 변경
`Set Config` 노드에서 카테고리 배열 수정:
```javascript
['PROGRAMMING', 'SCIENCE', 'LANGUAGE', ...]
```

### 프롬프트 튜닝
`1. Topic Select` 및 `2. Script + Quiz Gen` 노드의 jsonBody에서 프롬프트 수정

## 테스트 실행

1. Schedule Trigger 비활성화
2. `Set Config` 노드에서 **Test step** 클릭
3. 각 노드 순차적으로 테스트
