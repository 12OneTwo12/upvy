package me.onetwo.upvy.domain.quiz.service

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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 퀴즈 서비스 구현체
 *
 * @property quizRepository 퀴즈 레포지토리
 * @property quizOptionRepository 퀴즈 보기 레포지토리
 * @property quizAttemptRepository 퀴즈 시도 레포지토리
 * @property quizAttemptAnswerRepository 퀴즈 시도 답변 레포지토리
 */
@Service
@Transactional
class QuizServiceImpl(
    private val quizRepository: QuizRepository,
    private val quizOptionRepository: QuizOptionRepository,
    private val quizAttemptRepository: QuizAttemptRepository,
    private val quizAttemptAnswerRepository: QuizAttemptAnswerRepository
) : QuizService {

    private val logger = LoggerFactory.getLogger(QuizServiceImpl::class.java)

    override fun createQuiz(contentId: UUID, request: QuizCreateRequest, createdBy: UUID): Mono<QuizResponse> {
        logger.info("Creating quiz for contentId=$contentId")

        // 검증: 이미 퀴즈가 존재하는지 확인
        return quizRepository.existsByContentId(contentId)
            .flatMap { exists ->
                if (exists) {
                    Mono.error(QuizException.QuizAlreadyExistsException(contentId.toString()))
                } else {
                    // 검증: 최소 1개 이상의 정답이 있는지 확인
                    validateAtLeastOneCorrectOption(request.options)

                    // 퀴즈 생성
                    val quiz = Quiz(
                        contentId = contentId,
                        question = request.question,
                        allowMultipleAnswers = request.allowMultipleAnswers,
                        createdBy = createdBy.toString(),
                        updatedBy = createdBy.toString()
                    )

                    quizRepository.save(quiz)
                        .flatMap { savedQuiz ->
                            // 보기 생성
                            val options = request.options.mapIndexed { index, optionRequest ->
                                QuizOption(
                                    quizId = savedQuiz.id!!,
                                    optionText = optionRequest.optionText,
                                    isCorrect = optionRequest.isCorrect,
                                    displayOrder = index + 1,
                                    createdBy = createdBy.toString(),
                                    updatedBy = createdBy.toString()
                                )
                            }

                            Flux.fromIterable(options)
                                .flatMap { option -> quizOptionRepository.save(option) }
                                .collectList()
                                .map { savedOptions ->
                                    QuizResponse(
                                        id = savedQuiz.id.toString(),
                                        contentId = savedQuiz.contentId.toString(),
                                        question = savedQuiz.question,
                                        allowMultipleAnswers = savedQuiz.allowMultipleAnswers,
                                        options = savedOptions.map { option ->
                                            QuizOptionResponse(
                                                id = option.id.toString(),
                                                optionText = option.optionText,
                                                displayOrder = option.displayOrder
                                            )
                                        },
                                        userAttemptCount = 0,
                                        totalAttempts = 0
                                    )
                                }
                        }
                }
            }
    }

    override fun getQuizByContentId(contentId: UUID, userId: UUID?): Mono<QuizResponse> {
        logger.debug("Getting quiz for contentId=$contentId, userId=$userId")

        return quizRepository.findByContentId(contentId)
            .switchIfEmpty(Mono.error(QuizException.QuizNotFoundForContentException(contentId.toString())))
            .flatMap { quiz ->
                val quizId = quiz.id!!

                // 보기 조회
                val optionsMono = quizOptionRepository.findByQuizId(quizId).collectList()

                // 전체 시도 횟수 조회
                val totalAttemptsMono = quizAttemptRepository.findByQuizId(quizId).collectList()

                // 사용자별 시도 횟수 조회
                val userAttemptCountMono = if (userId != null) {
                    quizAttemptRepository.countByQuizIdAndUserId(quizId, userId)
                } else {
                    Mono.just(0)
                }

                // 사용자가 이미 시도했는지 확인
                val hasAttemptedMono = if (userId != null) {
                    quizAttemptRepository.findByQuizIdAndUserId(quizId, userId)
                        .hasElements()
                } else {
                    Mono.just(false)
                }

                Mono.zip(optionsMono, totalAttemptsMono, userAttemptCountMono, hasAttemptedMono)
                    .flatMap { tuple ->
                        val options = tuple.t1
                        val allAttempts = tuple.t2
                        val userAttemptCount = tuple.t3
                        val hasAttempted = tuple.t4

                        // 각 보기별 선택 횟수 계산
                        calculateOptionStats(quizId, options, allAttempts.size)
                            .map { optionResponses ->
                                QuizResponse(
                                    id = quizId.toString(),
                                    contentId = quiz.contentId.toString(),
                                    question = quiz.question,
                                    allowMultipleAnswers = quiz.allowMultipleAnswers,
                                    options = optionResponses.map { optionResponse ->
                                        // 사용자가 이미 시도했으면 정답 공개, 아니면 숨김
                                        if (hasAttempted) {
                                            optionResponse
                                        } else {
                                            optionResponse.copy(isCorrect = null)
                                        }
                                    },
                                    userAttemptCount = if (userId != null) userAttemptCount else null,
                                    totalAttempts = allAttempts.size
                                )
                            }
                    }
            }
    }

    override fun updateQuiz(contentId: UUID, request: QuizUpdateRequest, updatedBy: UUID): Mono<QuizResponse> {
        logger.info("Updating quiz for contentId=$contentId")

        // 검증: 최소 1개 이상의 정답이 있는지 확인
        validateAtLeastOneCorrectOption(request.options)

        return quizRepository.findByContentId(contentId)
            .switchIfEmpty(Mono.error(QuizException.QuizNotFoundForContentException(contentId.toString())))
            .flatMap { quiz ->
                val quizId = quiz.id!!

                // 퀴즈 업데이트
                val updatedQuiz = quiz.copy(
                    question = request.question,
                    allowMultipleAnswers = request.allowMultipleAnswers,
                    updatedBy = updatedBy.toString()
                )

                quizRepository.update(updatedQuiz)
                    .then(quizOptionRepository.deleteByQuizId(quizId, updatedBy))
                    .then(
                        Mono.defer {
                            // 새로운 보기 생성
                            val options = request.options.mapIndexed { index, optionRequest ->
                                QuizOption(
                                    quizId = quizId,
                                    optionText = optionRequest.optionText,
                                    isCorrect = optionRequest.isCorrect,
                                    displayOrder = index + 1,
                                    createdBy = updatedBy.toString(),
                                    updatedBy = updatedBy.toString()
                                )
                            }

                            Flux.fromIterable(options)
                                .flatMap { option -> quizOptionRepository.save(option) }
                                .collectList()
                                .map { savedOptions ->
                                    QuizResponse(
                                        id = quizId.toString(),
                                        contentId = updatedQuiz.contentId.toString(),
                                        question = updatedQuiz.question,
                                        allowMultipleAnswers = updatedQuiz.allowMultipleAnswers,
                                        options = savedOptions.map { option ->
                                            QuizOptionResponse(
                                                id = option.id.toString(),
                                                optionText = option.optionText,
                                                displayOrder = option.displayOrder
                                            )
                                        }
                                    )
                                }
                        }
                    )
            }
    }

    override fun deleteQuiz(contentId: UUID, deletedBy: UUID): Mono<Void> {
        logger.info("Deleting quiz for contentId=$contentId")

        return quizRepository.findByContentId(contentId)
            .switchIfEmpty(Mono.error(QuizException.QuizNotFoundForContentException(contentId.toString())))
            .flatMap { quiz ->
                val quizId = quiz.id!!

                quizRepository.delete(quizId, deletedBy)
                    .then(quizOptionRepository.deleteByQuizId(quizId, deletedBy))
            }
    }

    override fun submitQuizAttempt(quizId: UUID, userId: UUID, request: QuizAttemptRequest): Mono<QuizAttemptResponse> {
        logger.info("Submitting quiz attempt: quizId=$quizId, userId=$userId")

        return quizRepository.findById(quizId)
            .switchIfEmpty(Mono.error(QuizException.QuizNotFoundException(quizId.toString())))
            .flatMap { quiz ->
                // 선택한 보기 ID 파싱
                val selectedOptionIds = request.selectedOptionIds.map { UUID.fromString(it) }

                // 보기 조회 및 검증
                quizOptionRepository.findByIdIn(selectedOptionIds)
                    .collectList()
                    .flatMap { selectedOptions ->
                        // 검증: 선택한 보기가 모두 해당 퀴즈의 보기인지 확인
                        if (selectedOptions.size != selectedOptionIds.size) {
                            return@flatMap Mono.error<QuizAttemptResponse>(
                                QuizException.InvalidQuizDataException("일부 보기를 찾을 수 없습니다")
                            )
                        }

                        if (selectedOptions.any { it.quizId != quizId }) {
                            return@flatMap Mono.error<QuizAttemptResponse>(
                                QuizException.InvalidQuizDataException("다른 퀴즈의 보기가 포함되어 있습니다")
                            )
                        }

                        // 정답 확인
                        val allCorrectOptions = quizOptionRepository.findByQuizId(quizId)
                            .filter { it.isCorrect }
                            .collectList()

                        allCorrectOptions.flatMap { correctOptions ->
                            val correctOptionIds = correctOptions.map { it.id!! }.toSet()
                            val selectedOptionIdSet = selectedOptionIds.toSet()
                            val isCorrect = correctOptionIds == selectedOptionIdSet

                            // 다음 시도 번호 조회
                            quizAttemptRepository.getNextAttemptNumber(quizId, userId)
                                .flatMap { attemptNumber ->
                                    // 시도 저장
                                    val attempt = QuizAttempt(
                                        quizId = quizId,
                                        userId = userId,
                                        attemptNumber = attemptNumber,
                                        isCorrect = isCorrect
                                    )

                                    quizAttemptRepository.save(attempt)
                                        .flatMap { savedAttempt ->
                                            val attemptId = savedAttempt.id!!

                                            // 선택한 답변 저장
                                            val answers = selectedOptionIds.map { optionId ->
                                                QuizAttemptAnswer(
                                                    attemptId = attemptId,
                                                    optionId = optionId
                                                )
                                            }

                                            quizAttemptAnswerRepository.saveAll(answers)
                                                .collectList()
                                                .then(
                                                    // 통계와 함께 응답 생성
                                                    quizAttemptRepository.findByQuizId(quizId)
                                                        .collectList()
                                                        .flatMap { allAttempts ->
                                                            quizOptionRepository.findByQuizId(quizId)
                                                                .collectList()
                                                                .flatMap { allOptions ->
                                                                    calculateOptionStats(quizId, allOptions, allAttempts.size)
                                                                        .map { optionStats ->
                                                                            QuizAttemptResponse(
                                                                                attemptId = attemptId.toString(),
                                                                                quizId = quizId.toString(),
                                                                                isCorrect = isCorrect,
                                                                                attemptNumber = attemptNumber,
                                                                                options = optionStats
                                                                            )
                                                                        }
                                                                }
                                                        }
                                                )
                                        }
                                }
                        }
                    }
            }
    }

    override fun getUserQuizAttempts(quizId: UUID, userId: UUID): Mono<UserQuizAttemptsResponse> {
        logger.debug("Getting user quiz attempts: quizId=$quizId, userId=$userId")

        return quizAttemptRepository.findByQuizIdAndUserId(quizId, userId)
            .flatMap { attempt ->
                quizAttemptAnswerRepository.findByAttemptId(attempt.id!!)
                    .map { it.optionId.toString() }
                    .collectList()
                    .map { selectedOptions ->
                        UserQuizAttemptDetail(
                            attemptId = attempt.id.toString(),
                            attemptNumber = attempt.attemptNumber,
                            isCorrect = attempt.isCorrect,
                            selectedOptions = selectedOptions,
                            attemptedAt = attempt.createdAt.toString()
                        )
                    }
            }
            .collectList()
            .map { attempts ->
                UserQuizAttemptsResponse(attempts = attempts)
            }
    }

    override fun getQuizStats(quizId: UUID): Mono<QuizStatsResponse> {
        logger.debug("Getting quiz stats: quizId=$quizId")

        return quizRepository.findById(quizId)
            .switchIfEmpty(Mono.error(QuizException.QuizNotFoundException(quizId.toString())))
            .flatMap {
                val allAttemptsMono = quizAttemptRepository.findByQuizId(quizId).collectList()
                val optionsMono = quizOptionRepository.findByQuizId(quizId).collectList()

                Mono.zip(allAttemptsMono, optionsMono)
                    .flatMap { tuple ->
                        val allAttempts = tuple.t1
                        val options = tuple.t2
                        val uniqueUsers = allAttempts.map { it.userId }.toSet().size

                        calculateOptionStats(quizId, options, allAttempts.size)
                            .map { optionStats ->
                                QuizStatsResponse(
                                    quizId = quizId.toString(),
                                    totalAttempts = allAttempts.size,
                                    uniqueUsers = uniqueUsers,
                                    options = optionStats.map { stat ->
                                        QuizOptionStatsResponse(
                                            optionId = stat.id,
                                            optionText = stat.optionText,
                                            selectionCount = stat.selectionCount,
                                            selectionPercentage = stat.selectionPercentage,
                                            isCorrect = stat.isCorrect!!
                                        )
                                    }
                                )
                            }
                    }
            }
    }

    /**
     * 각 보기별 선택 통계 계산
     */
    private fun calculateOptionStats(
        quizId: UUID,
        options: List<QuizOption>,
        totalAttempts: Int
    ): Mono<List<QuizOptionResponse>> {
        if (options.isEmpty()) {
            return Mono.just(emptyList())
        }

        // 각 보기별 선택 횟수 조회
        return Flux.fromIterable(options)
            .flatMap { option ->
                quizAttemptRepository.findByQuizId(quizId)
                    .flatMap { attempt ->
                        quizAttemptAnswerRepository.findByAttemptId(attempt.id!!)
                            .filter { it.optionId == option.id }
                            .hasElements()
                            .map { if (it) 1 else 0 }
                    }
                    .reduce(0, Int::plus)
                    .map { selectionCount ->
                        val percentage = if (totalAttempts > 0) {
                            (selectionCount.toDouble() / totalAttempts) * 100.0
                        } else {
                            0.0
                        }

                        QuizOptionResponse(
                            id = option.id.toString(),
                            optionText = option.optionText,
                            displayOrder = option.displayOrder,
                            selectionCount = selectionCount,
                            selectionPercentage = percentage,
                            isCorrect = option.isCorrect
                        )
                    }
            }
            .collectList()
    }

    /**
     * 최소 1개 이상의 정답이 있는지 검증
     */
    private fun validateAtLeastOneCorrectOption(options: List<QuizOptionCreateRequest>) {
        val hasCorrectOption = options.any { it.isCorrect }
        if (!hasCorrectOption) {
            throw QuizException.InvalidQuizDataException("최소 1개 이상의 정답이 필요합니다")
        }
    }
}
