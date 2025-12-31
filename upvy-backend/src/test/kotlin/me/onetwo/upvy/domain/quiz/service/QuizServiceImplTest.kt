package me.onetwo.upvy.domain.quiz.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import me.onetwo.upvy.domain.quiz.dto.*
import me.onetwo.upvy.domain.quiz.exception.QuizException
import me.onetwo.upvy.domain.quiz.model.Quiz
import me.onetwo.upvy.domain.quiz.model.QuizAttempt
import me.onetwo.upvy.domain.quiz.model.QuizAttemptAnswer
import me.onetwo.upvy.domain.quiz.model.QuizOption
import me.onetwo.upvy.domain.quiz.repository.QuizAttemptAnswerRepository
import me.onetwo.upvy.domain.quiz.repository.QuizAttemptRepository
import me.onetwo.upvy.domain.quiz.repository.QuizOptionRepository
import me.onetwo.upvy.domain.quiz.repository.QuizRepository
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * QuizServiceImpl 단위 테스트
 *
 * MockK를 사용하여 의존성을 모킹하고,
 * StepVerifier를 사용하여 Reactive 타입을 검증합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("QuizServiceImpl 단위 테스트")
class QuizServiceImplTest : BaseReactiveTest {

    @MockK
    private lateinit var quizRepository: QuizRepository

    @MockK
    private lateinit var quizOptionRepository: QuizOptionRepository

    @MockK
    private lateinit var quizAttemptRepository: QuizAttemptRepository

    @MockK
    private lateinit var quizAttemptAnswerRepository: QuizAttemptAnswerRepository

    @InjectMockKs
    private lateinit var quizService: QuizServiceImpl

    private val contentId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val quizId = UUID.randomUUID()

    @Nested
    @DisplayName("createQuiz - 퀴즈 생성")
    inner class CreateQuiz {

        @Test
        @DisplayName("유효한 요청으로 퀴즈를 생성하면, QuizResponse를 반환한다")
        fun createQuiz_WithValidRequest_ReturnsQuizResponse() {
            // Given: 유효한 퀴즈 생성 요청
            val request = QuizCreateRequest(
                question = "Kotlin에서 val과 var의 차이는?",
                allowMultipleAnswers = false,
                options = listOf(
                    QuizOptionCreateRequest("val은 불변, var는 가변", true),
                    QuizOptionCreateRequest("val은 가변, var는 불변", false),
                    QuizOptionCreateRequest("둘 다 불변", false),
                    QuizOptionCreateRequest("둘 다 가변", false)
                )
            )

            val savedQuiz = Quiz(
                id = quizId,
                contentId = contentId,
                question = request.question,
                allowMultipleAnswers = request.allowMultipleAnswers,
                createdBy = userId.toString(),
                updatedBy = userId.toString(),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            val savedOptions = request.options.mapIndexed { index, optionRequest ->
                QuizOption(
                    id = UUID.randomUUID(),
                    quizId = quizId,
                    optionText = optionRequest.optionText,
                    isCorrect = optionRequest.isCorrect,
                    displayOrder = index + 1,
                    createdBy = userId.toString(),
                    updatedBy = userId.toString(),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            }

            every { quizRepository.existsByContentId(contentId) } returns Mono.just(false)
            every { quizRepository.save(any()) } returns Mono.just(savedQuiz)
            every { quizOptionRepository.save(any()) } returnsMany savedOptions.map { Mono.just(it) }

            // When: 퀴즈 생성
            val result = quizService.createQuiz(contentId, request, userId)

            // Then: QuizResponse 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.id).isEqualTo(quizId.toString())
                    assertThat(response.contentId).isEqualTo(contentId.toString())
                    assertThat(response.question).isEqualTo(request.question)
                    assertThat(response.allowMultipleAnswers).isEqualTo(request.allowMultipleAnswers)
                    assertThat(response.options).hasSize(4)
                    assertThat(response.userAttemptCount).isEqualTo(0)
                    assertThat(response.totalAttempts).isEqualTo(0)
                }
                .verifyComplete()

            verify(exactly = 1) { quizRepository.existsByContentId(contentId) }
            verify(exactly = 1) { quizRepository.save(any()) }
            verify(exactly = 4) { quizOptionRepository.save(any()) }
        }

        @Test
        @DisplayName("이미 퀴즈가 존재하면, QuizAlreadyExistsException을 발생시킨다")
        fun createQuiz_WhenQuizAlreadyExists_ThrowsQuizAlreadyExistsException() {
            // Given: 이미 퀴즈가 존재함
            val request = QuizCreateRequest(
                question = "Test Question",
                allowMultipleAnswers = false,
                options = listOf(
                    QuizOptionCreateRequest("옵션 1", true),
                    QuizOptionCreateRequest("옵션 2", false)
                )
            )

            every { quizRepository.existsByContentId(contentId) } returns Mono.just(true)

            // When & Then: 예외 발생
            StepVerifier.create(quizService.createQuiz(contentId, request, userId))
                .expectError(QuizException.QuizAlreadyExistsException::class.java)
                .verify()

            verify(exactly = 1) { quizRepository.existsByContentId(contentId) }
            verify(exactly = 0) { quizRepository.save(any()) }
        }

        @Test
        @DisplayName("정답 보기가 없으면, InvalidQuizDataException을 발생시킨다")
        fun createQuiz_WithNoCorrectAnswer_ThrowsInvalidQuizDataException() {
            // Given: 정답이 없는 요청
            val request = QuizCreateRequest(
                question = "Test Question",
                allowMultipleAnswers = false,
                options = listOf(
                    QuizOptionCreateRequest("옵션 1", false),
                    QuizOptionCreateRequest("옵션 2", false)
                )
            )

            every { quizRepository.existsByContentId(contentId) } returns Mono.just(false)

            // When & Then: 예외 발생
            StepVerifier.create(quizService.createQuiz(contentId, request, userId))
                .expectError(QuizException.InvalidQuizDataException::class.java)
                .verify()

            verify(exactly = 1) { quizRepository.existsByContentId(contentId) }
            verify(exactly = 0) { quizRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("getQuizByContentId - 콘텐츠 ID로 퀴즈 조회")
    inner class GetQuizByContentId {

        @Test
        @DisplayName("존재하는 퀴즈를 조회하면, QuizResponse를 반환한다")
        fun getQuizByContentId_WhenQuizExists_ReturnsQuizResponse() {
            // Given: 퀴즈와 보기가 존재함
            val quiz = Quiz(
                id = quizId,
                contentId = contentId,
                question = "Test Question",
                allowMultipleAnswers = false,
                createdBy = userId.toString(),
                updatedBy = userId.toString(),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            val options = listOf(
                QuizOption(
                    id = UUID.randomUUID(),
                    quizId = quizId,
                    optionText = "옵션 1",
                    isCorrect = true,
                    displayOrder = 1,
                    createdBy = userId.toString(),
                    updatedBy = userId.toString(),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                ),
                QuizOption(
                    id = UUID.randomUUID(),
                    quizId = quizId,
                    optionText = "옵션 2",
                    isCorrect = false,
                    displayOrder = 2,
                    createdBy = userId.toString(),
                    updatedBy = userId.toString(),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            )

            every { quizRepository.findByContentId(contentId) } returns Mono.just(quiz)
            every { quizOptionRepository.findByQuizId(quizId) } returns Flux.fromIterable(options)
            every { quizAttemptRepository.countByQuizId(quizId) } returns Mono.just(0)
            every { quizAttemptRepository.countByQuizIdAndUserId(quizId, userId) } returns Mono.just(0)
            every { quizAttemptRepository.findByQuizIdAndUserId(quizId, userId) } returns Flux.empty()
            every { quizAttemptAnswerRepository.getSelectionCountsByQuizId(quizId) } returns Mono.just(emptyMap())

            // When: 퀴즈 조회
            val result = quizService.getQuizByContentId(contentId, userId)

            // Then: QuizResponse 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.id).isEqualTo(quizId.toString())
                    assertThat(response.contentId).isEqualTo(contentId.toString())
                    assertThat(response.question).isEqualTo("Test Question")
                    assertThat(response.options).hasSize(2)
                    assertThat(response.totalAttempts).isEqualTo(0)
                    assertThat(response.userAttemptCount).isEqualTo(0)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("퀴즈가 존재하지 않으면, QuizNotFoundForContentException을 발생시킨다")
        fun getQuizByContentId_WhenQuizNotFound_ThrowsQuizNotFoundForContentException() {
            // Given: 퀴즈가 존재하지 않음
            every { quizRepository.findByContentId(contentId) } returns Mono.empty()

            // When & Then: 예외 발생
            StepVerifier.create(quizService.getQuizByContentId(contentId, userId))
                .expectError(QuizException.QuizNotFoundForContentException::class.java)
                .verify()
        }

        @Test
        @DisplayName("사용자가 시도하지 않았으면, 정답이 숨겨진다")
        fun getQuizByContentId_WhenUserNotAttempted_HidesCorrectAnswers() {
            // Given: 사용자가 시도하지 않음
            val quiz = Quiz(
                id = quizId,
                contentId = contentId,
                question = "Test Question",
                allowMultipleAnswers = false,
                createdBy = userId.toString(),
                updatedBy = userId.toString(),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            val options = listOf(
                QuizOption(
                    id = UUID.randomUUID(),
                    quizId = quizId,
                    optionText = "옵션 1",
                    isCorrect = true,
                    displayOrder = 1,
                    createdBy = userId.toString(),
                    updatedBy = userId.toString(),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            )

            every { quizRepository.findByContentId(contentId) } returns Mono.just(quiz)
            every { quizOptionRepository.findByQuizId(quizId) } returns Flux.fromIterable(options)
            every { quizAttemptRepository.countByQuizId(quizId) } returns Mono.just(0)
            every { quizAttemptRepository.countByQuizIdAndUserId(quizId, userId) } returns Mono.just(0)
            every { quizAttemptRepository.findByQuizIdAndUserId(quizId, userId) } returns Flux.empty()
            every { quizAttemptAnswerRepository.getSelectionCountsByQuizId(quizId) } returns Mono.just(emptyMap())

            // When: 퀴즈 조회
            val result = quizService.getQuizByContentId(contentId, userId)

            // Then: 정답 정보가 숨겨짐
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.options).hasSize(1)
                    assertThat(response.options[0].isCorrect).isNull()
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("updateQuiz - 퀴즈 수정")
    inner class UpdateQuiz {

        @Test
        @DisplayName("유효한 요청으로 퀴즈를 수정하면, QuizResponse를 반환한다")
        fun updateQuiz_WithValidRequest_ReturnsQuizResponse() {
            // Given: 기존 퀴즈와 수정 요청
            val existingQuiz = Quiz(
                id = quizId,
                contentId = contentId,
                question = "Old Question",
                allowMultipleAnswers = false,
                createdBy = userId.toString(),
                updatedBy = userId.toString(),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            val request = QuizUpdateRequest(
                question = "Updated Question",
                allowMultipleAnswers = true,
                options = listOf(
                    QuizOptionCreateRequest("새 옵션 1", true),
                    QuizOptionCreateRequest("새 옵션 2", false)
                )
            )

            val updatedQuiz = existingQuiz.copy(
                question = request.question,
                allowMultipleAnswers = request.allowMultipleAnswers,
                updatedBy = userId.toString()
            )

            val newOptions = request.options.mapIndexed { index, optionRequest ->
                QuizOption(
                    id = UUID.randomUUID(),
                    quizId = quizId,
                    optionText = optionRequest.optionText,
                    isCorrect = optionRequest.isCorrect,
                    displayOrder = index + 1,
                    createdBy = userId.toString(),
                    updatedBy = userId.toString(),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            }

            every { quizRepository.findByContentId(contentId) } returns Mono.just(existingQuiz)
            every { quizRepository.update(any()) } returns Mono.just(updatedQuiz)
            every { quizOptionRepository.deleteByQuizId(quizId, userId) } returns Mono.empty()
            every { quizOptionRepository.save(any()) } returnsMany newOptions.map { Mono.just(it) }

            // When: 퀴즈 수정
            val result = quizService.updateQuiz(contentId, request, userId)

            // Then: QuizResponse 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.id).isEqualTo(quizId.toString())
                    assertThat(response.question).isEqualTo("Updated Question")
                    assertThat(response.allowMultipleAnswers).isTrue()
                    assertThat(response.options).hasSize(2)
                }
                .verifyComplete()

            verify(exactly = 1) { quizRepository.findByContentId(contentId) }
            verify(exactly = 1) { quizRepository.update(any()) }
            verify(exactly = 1) { quizOptionRepository.deleteByQuizId(quizId, userId) }
            verify(exactly = 2) { quizOptionRepository.save(any()) }
        }

        @Test
        @DisplayName("퀴즈가 존재하지 않으면, QuizNotFoundForContentException을 발생시킨다")
        fun updateQuiz_WhenQuizNotFound_ThrowsQuizNotFoundForContentException() {
            // Given: 퀴즈가 존재하지 않음
            val request = QuizUpdateRequest(
                question = "Updated Question",
                allowMultipleAnswers = false,
                options = listOf(
                    QuizOptionCreateRequest("옵션 1", true),
                    QuizOptionCreateRequest("옵션 2", false)
                )
            )

            every { quizRepository.findByContentId(contentId) } returns Mono.empty()

            // When & Then: 예외 발생
            StepVerifier.create(quizService.updateQuiz(contentId, request, userId))
                .expectError(QuizException.QuizNotFoundForContentException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("deleteQuiz - 퀴즈 삭제")
    inner class DeleteQuiz {

        @Test
        @DisplayName("존재하는 퀴즈를 삭제하면, 완료 신호를 반환한다")
        fun deleteQuiz_WhenQuizExists_CompletesSuccessfully() {
            // Given: 퀴즈가 존재함
            val quiz = Quiz(
                id = quizId,
                contentId = contentId,
                question = "Test Question",
                allowMultipleAnswers = false,
                createdBy = userId.toString(),
                updatedBy = userId.toString(),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            every { quizRepository.findByContentId(contentId) } returns Mono.just(quiz)
            every { quizRepository.delete(quizId, userId) } returns Mono.empty()
            every { quizOptionRepository.deleteByQuizId(quizId, userId) } returns Mono.empty()

            // When: 퀴즈 삭제
            val result = quizService.deleteQuiz(contentId, userId)

            // Then: 완료
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) { quizRepository.findByContentId(contentId) }
            verify(exactly = 1) { quizRepository.delete(quizId, userId) }
            verify(exactly = 1) { quizOptionRepository.deleteByQuizId(quizId, userId) }
        }

        @Test
        @DisplayName("퀴즈가 존재하지 않으면, QuizNotFoundForContentException을 발생시킨다")
        fun deleteQuiz_WhenQuizNotFound_ThrowsQuizNotFoundForContentException() {
            // Given: 퀴즈가 존재하지 않음
            every { quizRepository.findByContentId(contentId) } returns Mono.empty()

            // When & Then: 예외 발생
            StepVerifier.create(quizService.deleteQuiz(contentId, userId))
                .expectError(QuizException.QuizNotFoundForContentException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("submitQuizAttempt - 퀴즈 시도 제출")
    inner class SubmitQuizAttempt {

        @Test
        @DisplayName("올바른 정답을 제출하면, 정답 응답을 반환한다")
        fun submitQuizAttempt_WithCorrectAnswer_ReturnsCorrectResponse() {
            // Given: 퀴즈와 정답 보기
            val quiz = Quiz(
                id = quizId,
                contentId = contentId,
                question = "Test Question",
                allowMultipleAnswers = false,
                createdBy = userId.toString(),
                updatedBy = userId.toString(),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            val correctOption = QuizOption(
                id = UUID.randomUUID(),
                quizId = quizId,
                optionText = "정답",
                isCorrect = true,
                displayOrder = 1,
                createdBy = userId.toString(),
                updatedBy = userId.toString(),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            val request = QuizAttemptRequest(selectedOptionIds = listOf(correctOption.id.toString()))

            val attempt = QuizAttempt(
                id = UUID.randomUUID(),
                quizId = quizId,
                userId = userId,
                attemptNumber = 1,
                isCorrect = true,
                createdAt = Instant.now()
            )

            val answer = QuizAttemptAnswer(
                id = UUID.randomUUID(),
                attemptId = attempt.id!!,
                optionId = correctOption.id!!,
                createdAt = Instant.now()
            )

            every { quizRepository.findById(quizId) } returns Mono.just(quiz)
            every { quizOptionRepository.findByIdIn(listOf(correctOption.id!!)) } returns Flux.just(correctOption)
            every { quizOptionRepository.findByQuizId(quizId) } returns Flux.just(correctOption)
            every { quizAttemptRepository.getNextAttemptNumber(quizId, userId) } returns Mono.just(1)
            every { quizAttemptRepository.save(any()) } returns Mono.just(attempt)
            every { quizAttemptAnswerRepository.saveAll(any<List<QuizAttemptAnswer>>()) } returns Flux.just(answer)
            every { quizAttemptRepository.countByQuizId(quizId) } returns Mono.just(1)
            every { quizAttemptAnswerRepository.getSelectionCountsByQuizId(quizId) } returns Mono.just(mapOf(correctOption.id!! to 1))

            // When: 정답 제출
            val result = quizService.submitQuizAttempt(quizId, userId, request)

            // Then: 정답 응답
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.isCorrect).isTrue()
                    assertThat(response.attemptNumber).isEqualTo(1)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("퀴즈가 존재하지 않으면, QuizNotFoundException을 발생시킨다")
        fun submitQuizAttempt_WhenQuizNotFound_ThrowsQuizNotFoundException() {
            // Given: 퀴즈가 존재하지 않음
            val request = QuizAttemptRequest(selectedOptionIds = listOf(UUID.randomUUID().toString()))

            every { quizRepository.findById(quizId) } returns Mono.empty()

            // When & Then: 예외 발생
            StepVerifier.create(quizService.submitQuizAttempt(quizId, userId, request))
                .expectError(QuizException.QuizNotFoundException::class.java)
                .verify()
        }
    }
}
