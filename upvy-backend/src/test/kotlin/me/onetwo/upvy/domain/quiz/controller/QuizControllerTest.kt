package me.onetwo.upvy.domain.quiz.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.quiz.dto.*
import me.onetwo.upvy.domain.quiz.service.QuizService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.infrastructure.config.RestDocsConfiguration
import me.onetwo.upvy.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.request.RequestDocumentation.*
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.util.UUID

@WebFluxTest(QuizController::class)
@Import(RestDocsConfiguration::class, TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("퀴즈 Controller 테스트")
class QuizControllerTest : BaseReactiveTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var quizService: QuizService

    @Nested
    @DisplayName("POST /api/v1/contents/{contentId}/quiz - 퀴즈 생성")
    inner class CreateQuiz {

        @Test
        @DisplayName("유효한 요청으로 퀴즈 생성 시, 201 Created와 퀴즈 응답을 반환한다")
        fun createQuiz_WithValidRequest_ReturnsQuizResponse() {
            // Given: 퀴즈 생성 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val quizId = UUID.randomUUID().toString()
            val request = QuizCreateRequest(
                question = "Kotlin에서 val과 var의 차이는 무엇인가요?",
                allowMultipleAnswers = false,
                options = listOf(
                    QuizOptionCreateRequest(optionText = "val은 불변, var는 가변", isCorrect = true),
                    QuizOptionCreateRequest(optionText = "val은 가변, var는 불변", isCorrect = false),
                    QuizOptionCreateRequest(optionText = "차이가 없다", isCorrect = false),
                    QuizOptionCreateRequest(optionText = "val만 사용 가능", isCorrect = false)
                )
            )
            val response = QuizResponse(
                id = quizId,
                contentId = contentId.toString(),
                question = request.question,
                allowMultipleAnswers = false,
                options = listOf(
                    QuizOptionResponse(
                        id = UUID.randomUUID().toString(),
                        optionText = "val은 불변, var는 가변",
                        displayOrder = 1,
                        selectionCount = 0,
                        selectionPercentage = 0.0,
                        isCorrect = null
                    ),
                    QuizOptionResponse(
                        id = UUID.randomUUID().toString(),
                        optionText = "val은 가변, var는 불변",
                        displayOrder = 2,
                        selectionCount = 0,
                        selectionPercentage = 0.0,
                        isCorrect = null
                    ),
                    QuizOptionResponse(
                        id = UUID.randomUUID().toString(),
                        optionText = "차이가 없다",
                        displayOrder = 3,
                        selectionCount = 0,
                        selectionPercentage = 0.0,
                        isCorrect = null
                    ),
                    QuizOptionResponse(
                        id = UUID.randomUUID().toString(),
                        optionText = "val만 사용 가능",
                        displayOrder = 4,
                        selectionCount = 0,
                        selectionPercentage = 0.0,
                        isCorrect = null
                    )
                ),
                userAttemptCount = 0,
                totalAttempts = 0
            )

            every { quizService.createQuiz(contentId, request, userId) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/quiz", contentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isEqualTo(quizId)
                .jsonPath("$.contentId").isEqualTo(contentId.toString())
                .jsonPath("$.question").isEqualTo(request.question)
                .jsonPath("$.allowMultipleAnswers").isEqualTo(false)
                .jsonPath("$.options").isArray
                .jsonPath("$.options.length()").isEqualTo(4)
                .consumeWith(
                    document(
                        "quiz-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("콘텐츠 ID")
                        ),
                        requestFields(
                            fieldWithPath("question").description("퀴즈 질문 (최대 200자)"),
                            fieldWithPath("allowMultipleAnswers").description("복수 정답 허용 여부"),
                            fieldWithPath("options").description("퀴즈 보기 목록 (최소 2개)"),
                            fieldWithPath("options[].optionText").description("보기 텍스트 (최대 100자)"),
                            fieldWithPath("options[].isCorrect").description("정답 여부")
                        ),
                        responseFields(
                            fieldWithPath("id").description("퀴즈 ID"),
                            fieldWithPath("contentId").description("콘텐츠 ID"),
                            fieldWithPath("question").description("퀴즈 질문"),
                            fieldWithPath("allowMultipleAnswers").description("복수 정답 허용 여부"),
                            fieldWithPath("options").description("퀴즈 보기 목록"),
                            fieldWithPath("options[].id").description("보기 ID"),
                            fieldWithPath("options[].optionText").description("보기 텍스트"),
                            fieldWithPath("options[].displayOrder").description("표시 순서"),
                            fieldWithPath("options[].selectionCount").description("선택 횟수"),
                            fieldWithPath("options[].selectionPercentage").description("선택 비율 (0.0 ~ 100.0)"),
                            fieldWithPath("options[].isCorrect").description("정답 여부 (미시도 시 null)").optional(),
                            fieldWithPath("userAttemptCount").description("현재 사용자 시도 횟수").optional(),
                            fieldWithPath("totalAttempts").description("전체 시도 횟수")
                        )
                    )
                )

            verify(exactly = 1) { quizService.createQuiz(contentId, request, userId) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId}/quiz - 퀴즈 조회")
    inner class GetQuiz {

        @Test
        @DisplayName("인증된 사용자가 퀴즈 조회 시, 200 OK와 퀴즈 응답을 반환한다")
        fun getQuiz_WithAuthenticatedUser_ReturnsQuizResponse() {
            // Given: 퀴즈 조회 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val response = QuizResponse(
                id = UUID.randomUUID().toString(),
                contentId = contentId.toString(),
                question = "Kotlin에서 val과 var의 차이는 무엇인가요?",
                allowMultipleAnswers = false,
                options = listOf(
                    QuizOptionResponse(
                        id = UUID.randomUUID().toString(),
                        optionText = "val은 불변, var는 가변",
                        displayOrder = 1,
                        selectionCount = 150,
                        selectionPercentage = 75.0,
                        isCorrect = null
                    ),
                    QuizOptionResponse(
                        id = UUID.randomUUID().toString(),
                        optionText = "val은 가변, var는 불변",
                        displayOrder = 2,
                        selectionCount = 50,
                        selectionPercentage = 25.0,
                        isCorrect = null
                    )
                ),
                userAttemptCount = 0,
                totalAttempts = 200
            )

            every { quizService.getQuizByContentId(contentId, userId) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/quiz", contentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").exists()
                .jsonPath("$.contentId").isEqualTo(contentId.toString())
                .jsonPath("$.question").isEqualTo(response.question)
                .jsonPath("$.options").isArray
                .consumeWith(
                    document(
                        "quiz-get",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("콘텐츠 ID")
                        ),
                        responseFields(
                            fieldWithPath("id").description("퀴즈 ID"),
                            fieldWithPath("contentId").description("콘텐츠 ID"),
                            fieldWithPath("question").description("퀴즈 질문"),
                            fieldWithPath("allowMultipleAnswers").description("복수 정답 허용 여부"),
                            fieldWithPath("options").description("퀴즈 보기 목록"),
                            fieldWithPath("options[].id").description("보기 ID"),
                            fieldWithPath("options[].optionText").description("보기 텍스트"),
                            fieldWithPath("options[].displayOrder").description("표시 순서"),
                            fieldWithPath("options[].selectionCount").description("선택 횟수"),
                            fieldWithPath("options[].selectionPercentage").description("선택 비율 (0.0 ~ 100.0)"),
                            fieldWithPath("options[].isCorrect").description("정답 여부 (미시도 시 null)").optional(),
                            fieldWithPath("userAttemptCount").description("현재 사용자 시도 횟수").optional(),
                            fieldWithPath("totalAttempts").description("전체 시도 횟수")
                        )
                    )
                )

            verify(exactly = 1) { quizService.getQuizByContentId(contentId, userId) }
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/contents/{contentId}/quiz - 퀴즈 수정")
    inner class UpdateQuiz {

        @Test
        @DisplayName("유효한 요청으로 퀴즈 수정 시, 200 OK와 수정된 퀴즈 응답을 반환한다")
        fun updateQuiz_WithValidRequest_ReturnsUpdatedQuizResponse() {
            // Given: 퀴즈 수정 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val request = QuizUpdateRequest(
                question = "Kotlin의 주요 특징은?",
                allowMultipleAnswers = true,
                options = listOf(
                    QuizOptionCreateRequest(optionText = "Null Safety", isCorrect = true),
                    QuizOptionCreateRequest(optionText = "간결한 문법", isCorrect = true),
                    QuizOptionCreateRequest(optionText = "Java 호환성", isCorrect = true),
                    QuizOptionCreateRequest(optionText = "느린 성능", isCorrect = false)
                )
            )
            val response = QuizResponse(
                id = UUID.randomUUID().toString(),
                contentId = contentId.toString(),
                question = request.question,
                allowMultipleAnswers = true,
                options = listOf(
                    QuizOptionResponse(
                        id = UUID.randomUUID().toString(),
                        optionText = "Null Safety",
                        displayOrder = 1,
                        selectionCount = 0,
                        selectionPercentage = 0.0
                    )
                ),
                userAttemptCount = 0,
                totalAttempts = 0
            )

            every { quizService.updateQuiz(contentId, request, userId) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .put()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/quiz", contentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.question").isEqualTo(request.question)
                .jsonPath("$.allowMultipleAnswers").isEqualTo(true)
                .consumeWith(
                    document(
                        "quiz-update",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("콘텐츠 ID")
                        ),
                        requestFields(
                            fieldWithPath("question").description("수정할 퀴즈 질문 (최대 200자)"),
                            fieldWithPath("allowMultipleAnswers").description("복수 정답 허용 여부"),
                            fieldWithPath("options").description("수정할 보기 목록 (기존 보기는 삭제됨)"),
                            fieldWithPath("options[].optionText").description("보기 텍스트 (최대 100자)"),
                            fieldWithPath("options[].isCorrect").description("정답 여부")
                        ),
                        responseFields(
                            fieldWithPath("id").description("퀴즈 ID"),
                            fieldWithPath("contentId").description("콘텐츠 ID"),
                            fieldWithPath("question").description("수정된 퀴즈 질문"),
                            fieldWithPath("allowMultipleAnswers").description("복수 정답 허용 여부"),
                            fieldWithPath("options").description("수정된 보기 목록"),
                            fieldWithPath("options[].id").description("보기 ID"),
                            fieldWithPath("options[].optionText").description("보기 텍스트"),
                            fieldWithPath("options[].displayOrder").description("표시 순서"),
                            fieldWithPath("options[].selectionCount").description("선택 횟수"),
                            fieldWithPath("options[].selectionPercentage").description("선택 비율"),
                            fieldWithPath("options[].isCorrect").description("정답 여부").optional(),
                            fieldWithPath("userAttemptCount").description("현재 사용자 시도 횟수").optional(),
                            fieldWithPath("totalAttempts").description("전체 시도 횟수")
                        )
                    )
                )

            verify(exactly = 1) { quizService.updateQuiz(contentId, request, userId) }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/contents/{contentId}/quiz - 퀴즈 삭제")
    inner class DeleteQuiz {

        @Test
        @DisplayName("유효한 요청으로 퀴즈 삭제 시, 204 No Content를 반환한다")
        fun deleteQuiz_WithValidRequest_ReturnsNoContent() {
            // Given: 퀴즈 삭제 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { quizService.deleteQuiz(contentId, userId) } returns Mono.empty()

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/quiz", contentId)
                .exchange()
                .expectStatus().isNoContent
                .expectBody()
                .consumeWith(
                    document(
                        "quiz-delete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("콘텐츠 ID")
                        )
                    )
                )

            verify(exactly = 1) { quizService.deleteQuiz(contentId, userId) }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/quizzes/{quizId}/attempts - 퀴즈 시도 제출")
    inner class SubmitQuizAttempt {

        @Test
        @DisplayName("유효한 답변으로 퀴즈 시도 시, 201 Created와 시도 응답을 반환한다")
        fun submitQuizAttempt_WithValidRequest_ReturnsAttemptResponse() {
            // Given: 퀴즈 시도 요청
            val userId = UUID.randomUUID()
            val quizId = UUID.randomUUID()
            val optionId1 = UUID.randomUUID().toString()
            val request = QuizAttemptRequest(selectedOptionIds = listOf(optionId1))
            val response = QuizAttemptResponse(
                attemptId = UUID.randomUUID().toString(),
                quizId = quizId.toString(),
                isCorrect = true,
                attemptNumber = 1,
                options = listOf(
                    QuizOptionResponse(
                        id = optionId1,
                        optionText = "val은 불변, var는 가변",
                        displayOrder = 1,
                        selectionCount = 151,
                        selectionPercentage = 75.5,
                        isCorrect = true
                    ),
                    QuizOptionResponse(
                        id = UUID.randomUUID().toString(),
                        optionText = "val은 가변, var는 불변",
                        displayOrder = 2,
                        selectionCount = 49,
                        selectionPercentage = 24.5,
                        isCorrect = false
                    )
                )
            )

            every { quizService.submitQuizAttempt(quizId, userId, request) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/quizzes/{quizId}/attempts", quizId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.attemptId").exists()
                .jsonPath("$.quizId").isEqualTo(quizId.toString())
                .jsonPath("$.isCorrect").isEqualTo(true)
                .jsonPath("$.attemptNumber").isEqualTo(1)
                .jsonPath("$.options").isArray
                .consumeWith(
                    document(
                        "quiz-submit-attempt",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("quizId").description("퀴즈 ID")
                        ),
                        requestFields(
                            fieldWithPath("selectedOptionIds").description("선택한 보기 ID 목록")
                        ),
                        responseFields(
                            fieldWithPath("attemptId").description("시도 ID"),
                            fieldWithPath("quizId").description("퀴즈 ID"),
                            fieldWithPath("isCorrect").description("정답 여부"),
                            fieldWithPath("attemptNumber").description("시도 번호 (1, 2, 3, ...)"),
                            fieldWithPath("options").description("정답이 포함된 보기 목록"),
                            fieldWithPath("options[].id").description("보기 ID"),
                            fieldWithPath("options[].optionText").description("보기 텍스트"),
                            fieldWithPath("options[].displayOrder").description("표시 순서"),
                            fieldWithPath("options[].selectionCount").description("선택 횟수 (업데이트됨)"),
                            fieldWithPath("options[].selectionPercentage").description("선택 비율 (업데이트됨)"),
                            fieldWithPath("options[].isCorrect").description("정답 여부 (공개됨)").optional()
                        )
                    )
                )

            verify(exactly = 1) { quizService.submitQuizAttempt(quizId, userId, request) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/quizzes/{quizId}/my-attempts - 내 퀴즈 시도 이력 조회")
    inner class GetMyQuizAttempts {

        @Test
        @DisplayName("내 시도 이력 조회 시, 200 OK와 시도 목록을 반환한다")
        fun getMyQuizAttempts_WithValidRequest_ReturnsAttemptsList() {
            // Given: 시도 이력 조회 요청
            val userId = UUID.randomUUID()
            val quizId = UUID.randomUUID()
            val response = UserQuizAttemptsResponse(
                attempts = listOf(
                    UserQuizAttemptDetail(
                        attemptId = UUID.randomUUID().toString(),
                        attemptNumber = 2,
                        isCorrect = true,
                        selectedOptions = listOf(UUID.randomUUID().toString()),
                        attemptedAt = "2025-12-29T13:30:00Z"
                    ),
                    UserQuizAttemptDetail(
                        attemptId = UUID.randomUUID().toString(),
                        attemptNumber = 1,
                        isCorrect = false,
                        selectedOptions = listOf(UUID.randomUUID().toString()),
                        attemptedAt = "2025-12-29T13:25:00Z"
                    )
                )
            )

            every { quizService.getUserQuizAttempts(quizId, userId) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1}/quizzes/{quizId}/my-attempts", quizId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.attempts").isArray
                .jsonPath("$.attempts.length()").isEqualTo(2)
                .consumeWith(
                    document(
                        "quiz-get-my-attempts",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("quizId").description("퀴즈 ID")
                        ),
                        responseFields(
                            fieldWithPath("attempts").description("시도 기록 목록 (최신순)"),
                            fieldWithPath("attempts[].attemptId").description("시도 ID"),
                            fieldWithPath("attempts[].attemptNumber").description("시도 번호"),
                            fieldWithPath("attempts[].isCorrect").description("정답 여부"),
                            fieldWithPath("attempts[].selectedOptions").description("선택한 보기 ID 목록"),
                            fieldWithPath("attempts[].attemptedAt").description("시도 시각 (ISO-8601)")
                        )
                    )
                )

            verify(exactly = 1) { quizService.getUserQuizAttempts(quizId, userId) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/quizzes/{quizId}/stats - 퀴즈 통계 조회")
    inner class GetQuizStats {

        @Test
        @DisplayName("퀴즈 통계 조회 시, 200 OK와 통계 응답을 반환한다")
        fun getQuizStats_WithValidRequest_ReturnsStatsResponse() {
            // Given: 통계 조회 요청
            val quizId = UUID.randomUUID()
            val response = QuizStatsResponse(
                quizId = quizId.toString(),
                totalAttempts = 201,
                uniqueUsers = 150,
                options = listOf(
                    QuizOptionStatsResponse(
                        optionId = UUID.randomUUID().toString(),
                        optionText = "val은 불변, var는 가변",
                        selectionCount = 151,
                        selectionPercentage = 75.1,
                        isCorrect = true
                    ),
                    QuizOptionStatsResponse(
                        optionId = UUID.randomUUID().toString(),
                        optionText = "val은 가변, var는 불변",
                        selectionCount = 50,
                        selectionPercentage = 24.9,
                        isCorrect = false
                    )
                )
            )

            every { quizService.getQuizStats(quizId) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1}/quizzes/{quizId}/stats", quizId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.quizId").isEqualTo(quizId.toString())
                .jsonPath("$.totalAttempts").isEqualTo(201)
                .jsonPath("$.uniqueUsers").isEqualTo(150)
                .jsonPath("$.options").isArray
                .consumeWith(
                    document(
                        "quiz-get-stats",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("quizId").description("퀴즈 ID")
                        ),
                        responseFields(
                            fieldWithPath("quizId").description("퀴즈 ID"),
                            fieldWithPath("totalAttempts").description("전체 시도 횟수"),
                            fieldWithPath("uniqueUsers").description("참여한 고유 사용자 수"),
                            fieldWithPath("options").description("보기별 통계"),
                            fieldWithPath("options[].optionId").description("보기 ID"),
                            fieldWithPath("options[].optionText").description("보기 텍스트"),
                            fieldWithPath("options[].selectionCount").description("선택 횟수"),
                            fieldWithPath("options[].selectionPercentage").description("선택 비율 (0.0 ~ 100.0)"),
                            fieldWithPath("options[].isCorrect").description("정답 여부")
                        )
                    )
                )

            verify(exactly = 1) { quizService.getQuizStats(quizId) }
        }
    }
}
