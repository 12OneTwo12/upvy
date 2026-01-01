---
name: Feature request
about: 기능 개발 혹은 수정 관련 이슈
title: "[Feature] AI 크롤러에서 콘텐츠 생성 시 퀴즈 자동 생성 기능 추가"
labels: enhancement, ai-crawler
assignees: ''

---

### 개요

AI 크롤러가 유튜브 콘텐츠를 수집하고 변환하는 과정에서 LLM을 활용하여 교육용 퀴즈를 자동으로 생성하고, 콘텐츠 발행 시 함께 저장하는 기능을 추가합니다.

---

### 상세 내용

#### 요구 사항

1. **퀴즈 자동 생성**
   - AnalyzeStep에서 transcript 기반으로 LLM을 통해 퀴즈 생성
   - 생성된 퀴즈는 `ai_content_jobs.quiz` 필드에 JSON 형식으로 저장
   - 퀴즈 구조:
     - `question`: 질문 (최대 200자)
     - `allowMultipleAnswers`: 복수 정답 허용 여부
     - `options`: 보기 목록 (2~4개)
       - `optionText`: 보기 텍스트 (최대 100자)
       - `isCorrect`: 정답 여부

2. **퀴즈 발행**
   - ContentPublishService에서 콘텐츠 게시 시 퀴즈도 함께 생성
   - 백엔드 Quiz API (`POST /api/v1/contents/{contentId}/quiz`) 호출
   - System User로 퀴즈 생성

3. **품질 기준**
   - 퀴즈는 콘텐츠 내용과 직접적으로 연관
   - 정답은 1~2개 (복수 정답 가능)
   - 보기는 명확하고 중복 없이 구성
   - 난이도는 콘텐츠 difficulty에 맞춤

#### 구현 방향

**1. 데이터베이스 스키마 수정**

```sql
-- ai_content_jobs 테이블에 quiz 컬럼 추가
ALTER TABLE ai_content_jobs
ADD COLUMN quiz TEXT COMMENT '생성된 퀴즈 JSON';
```

**2. LlmClient 확장** (`upvy-ai-crawler/src/main/kotlin/me/onetwo/upvy/crawler/client/LlmClient.kt`)

```kotlin
/**
 * 퀴즈 생성
 *
 * @param transcript 자막 텍스트
 * @param contentLanguage 콘텐츠 언어
 * @param difficulty 난이도
 * @return 생성된 퀴즈
 */
suspend fun generateQuiz(
    transcript: String,
    contentLanguage: ContentLanguage,
    difficulty: ContentDifficulty?
): QuizGenerationResult
```

**3. AnalyzeProcessor 수정** (`upvy-ai-crawler/.../batch/step/analyze/AnalyzeProcessor.kt`)

```kotlin
// 기존: 메타데이터 생성
val metadata = runBlocking {
    llmClient.generateMetadata(job.transcript!!, contentLanguage)
}

// 추가: 퀴즈 생성
val quiz = runBlocking {
    llmClient.generateQuiz(
        transcript = job.transcript!!,
        contentLanguage = contentLanguage,
        difficulty = metadata.difficulty
    )
}
val quizJson = objectMapper.writeValueAsString(quiz)

// Job 업데이트 시 quiz 필드 추가
return job.copy(
    // ... 기존 필드들
    quiz = quizJson,
    // ...
)
```

**4. ContentPublishService 확장** (`upvy-ai-crawler/.../backoffice/service/ContentPublishService.kt`)

```kotlin
@Transactional
fun publishContent(pendingContentId: Long): String {
    // ... 기존 콘텐츠 발행 로직 (1~4단계)

    // 5. 퀴즈 생성 (quiz 데이터가 있는 경우)
    if (!pendingContent.quiz.isNullOrBlank()) {
        createQuizForContent(contentId, pendingContent.quiz)
        logger.debug("quiz INSERT 완료: contentId={}", contentId)
    }

    return contentId
}

/**
 * 콘텐츠에 퀴즈 생성
 */
private fun createQuizForContent(contentId: String, quizJson: String) {
    val quiz = objectMapper.readValue<QuizData>(quizJson)

    // Backend Quiz API 호출
    val request = QuizCreateRequest(
        question = quiz.question,
        allowMultipleAnswers = quiz.allowMultipleAnswers,
        options = quiz.options.map {
            QuizOptionCreateRequest(
                optionText = it.optionText,
                isCorrect = it.isCorrect
            )
        }
    )

    // WebClient로 Backend Quiz API 호출
    // POST /api/v1/contents/{contentId}/quiz
}
```

**5. Domain 모델 추가**

```kotlin
// upvy-ai-crawler/.../crawler/domain/Quiz.kt
data class QuizData(
    val question: String,
    val allowMultipleAnswers: Boolean,
    val options: List<QuizOption>
)

data class QuizOption(
    val optionText: String,
    val isCorrect: Boolean
)

data class QuizGenerationResult(
    val quiz: QuizData
)
```

**6. LLM Prompt 작성**

```
You are an educational content expert. Generate a quiz based on the following transcript.

Requirements:
- Create 1 question that tests understanding of key concepts
- Provide 2-4 answer options
- Mark correct answer(s)
- Question should be clear and concise (max 200 characters)
- Options should be distinct and unambiguous (max 100 characters each)
- Difficulty: {difficulty}
- Language: {language}

Transcript:
{transcript}

Return JSON format:
{
  "question": "...",
  "allowMultipleAnswers": false,
  "options": [
    {"optionText": "...", "isCorrect": true},
    {"optionText": "...", "isCorrect": false}
  ]
}
```

---

### 참고 사항

- 퀴즈 생성은 선택적 기능 (실패해도 콘텐츠 발행은 진행)
- 퀴즈 생성 실패 시 로그 남기고 계속 진행
- 백엔드 Quiz API는 이미 구현되어 있음 (`QuizController.createQuiz`)
- System User ID: `00000000-0000-0000-0000-000000000001` (설정 가능)

---

### 관련 문서

**Backend Quiz API**
- Controller: `upvy-backend/src/main/kotlin/me/onetwo/upvy/domain/quiz/controller/QuizController.kt`
- DTO: `upvy-backend/src/main/kotlin/me/onetwo/upvy/domain/quiz/dto/QuizDto.kt`
- Endpoint: `POST /api/v1/contents/{contentId}/quiz`

**AI Crawler 배치 프로세스**
- AnalyzeProcessor: `upvy-ai-crawler/src/main/kotlin/me/onetwo/upvy/crawler/batch/step/analyze/AnalyzeProcessor.kt`
- ContentPublishService: `upvy-ai-crawler/src/main/kotlin/me/onetwo/upvy/crawler/backoffice/service/ContentPublishService.kt`
- LlmClient: `upvy-ai-crawler/src/main/kotlin/me/onetwo/upvy/crawler/client/LlmClient.kt`

**데이터베이스 스키마**
- `ai_content_jobs`: AI 크롤러 작업 테이블
- `quizzes`: 퀴즈 메인 테이블 (백엔드)
- `quiz_options`: 퀴즈 보기 테이블 (백엔드)

---

### 구현 체크리스트

- [ ] `ai_content_jobs` 테이블에 `quiz` 컬럼 추가 (DDL)
- [ ] `QuizData`, `QuizOption`, `QuizGenerationResult` 도메인 모델 추가
- [ ] `LlmClient.generateQuiz()` 메서드 구현
- [ ] `AnalyzeProcessor`에서 퀴즈 생성 로직 추가
- [ ] `ContentPublishService.createQuizForContent()` 메서드 구현
- [ ] Backend Quiz API 호출을 위한 WebClient 설정
- [ ] 퀴즈 생성 실패 시 에러 핸들링
- [ ] 단위 테스트 작성 (`AnalyzeProcessorTest`, `ContentPublishServiceTest`)
- [ ] 통합 테스트 (E2E 배치 실행)
- [ ] Backoffice UI에서 퀴즈 확인 가능하도록 표시

---

### 예상 효과

1. **자동화된 교육 콘텐츠**: 수동 퀴즈 작성 없이 AI가 자동으로 생성
2. **학습 효과 증대**: 모든 콘텐츠에 퀴즈가 추가되어 학습 참여도 향상
3. **운영 효율성**: 관리자 승인 단계에서 퀴즈도 함께 검토 가능
4. **일관된 품질**: LLM을 통한 퀴즈 생성으로 품질 표준화
