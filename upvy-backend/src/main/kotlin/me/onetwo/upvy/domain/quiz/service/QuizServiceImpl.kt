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

    /**
     * 퀴즈 상세 조회 시 필요한 데이터를 저장하는 Data Class
     */
    private data class QuizDetailsData(
        val options: List<QuizOption>,
        val totalAttempts: Int,
        val userAttemptCount: Int,
        val hasAttempted: Boolean,
        val selectedOptions: List<UUID> = emptyList()
    )

    /**
     * 퀴즈 통계 조회 시 필요한 데이터를 저장하는 Data Class
     */
    private data class QuizStatsData(
        val totalAttempts: Int,
        val uniqueUsers: Int,
        val options: List<QuizOption>
    )

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

        // 퀴즈가 없으면 Mono.empty() 반환 (Controller에서 404 처리)
        return quizRepository.findByContentId(contentId)
            .flatMap { quiz ->
                val quizId = quiz.id!!

                // 보기 조회
                val optionsMono = quizOptionRepository.findByQuizId(quizId).collectList()

                // 전체 시도 횟수 조회 (최적화: count 쿼리 사용)
                val totalAttemptsMono = quizAttemptRepository.countByQuizId(quizId)

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

                // 최신 시도의 선택한 옵션 조회
                val selectedOptionsMono = if (userId != null) {
                    quizAttemptRepository.findByQuizIdAndUserId(quizId, userId)
                        .next() // 최신 시도 (첫 번째)
                        .flatMap { attempt ->
                            quizAttemptAnswerRepository.findByAttemptId(attempt.id!!)
                                .map { it.optionId }
                                .collectList()
                        }
                        .defaultIfEmpty(emptyList())
                } else {
                    Mono.just(emptyList())
                }

                Mono.zip(optionsMono, totalAttemptsMono, userAttemptCountMono, hasAttemptedMono, selectedOptionsMono)
                    .map { tuple ->
                        QuizDetailsData(tuple.t1, tuple.t2, tuple.t3, tuple.t4, tuple.t5)
                    }
                    .flatMap { data ->
                        // 각 보기별 선택 횟수 계산 (최적화된 GROUP BY 쿼리 사용)
                        calculateOptionStats(quizId, data.options, data.totalAttempts)
                            .map { optionResponses ->
                                QuizResponse(
                                    id = quizId.toString(),
                                    contentId = quiz.contentId.toString(),
                                    question = quiz.question,
                                    allowMultipleAnswers = quiz.allowMultipleAnswers,
                                    options = optionResponses.map { optionResponse ->
                                        // 사용자가 이미 시도했으면 정답 공개 + isSelected 설정, 아니면 숨김
                                        if (data.hasAttempted) {
                                            optionResponse.copy(
                                                isSelected = data.selectedOptions.contains(UUID.fromString(optionResponse.id))
                                            )
                                        } else {
                                            optionResponse.copy(isCorrect = null)
                                        }
                                    },
                                    userAttemptCount = if (userId != null) data.userAttemptCount else null,
                                    totalAttempts = data.totalAttempts
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
            .flatMap { _ ->
                val selectedOptionIds = request.selectedOptionIds.map { UUID.fromString(it) }

                validateSelectedOptions(quizId, selectedOptionIds)
                    .flatMap { _ ->
                        checkAnswerCorrectness(quizId, selectedOptionIds)
                            .flatMap { isCorrect ->
                                saveAttemptWithAnswers(quizId, userId, selectedOptionIds, isCorrect)
                                    .flatMap { attemptResult ->
                                        buildAttemptResponse(
                                            attemptId = attemptResult.first,
                                            quizId = quizId,
                                            isCorrect = isCorrect,
                                            attemptNumber = attemptResult.second,
                                            selectedOptionIds = selectedOptionIds
                                        )
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
                val totalAttemptsMono = quizAttemptRepository.countByQuizId(quizId)
                val uniqueUsersMono = quizAttemptRepository.countDistinctUsersByQuizId(quizId)
                val optionsMono = quizOptionRepository.findByQuizId(quizId).collectList()

                Mono.zip(totalAttemptsMono, uniqueUsersMono, optionsMono)
                    .map { tuple ->
                        QuizStatsData(tuple.t1, tuple.t2, tuple.t3)
                    }
                    .flatMap { data ->
                        calculateOptionStats(quizId, data.options, data.totalAttempts)
                            .map { optionStats ->
                                QuizStatsResponse(
                                    quizId = quizId.toString(),
                                    totalAttempts = data.totalAttempts,
                                    uniqueUsers = data.uniqueUsers,
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
     * 각 보기별 선택 통계 계산 (최적화: 단일 GROUP BY 쿼리 사용)
     */
    private fun calculateOptionStats(
        quizId: UUID,
        options: List<QuizOption>,
        totalAttempts: Int
    ): Mono<List<QuizOptionResponse>> {
        if (options.isEmpty()) {
            return Mono.just(emptyList())
        }

        // 모든 보기의 선택 횟수를 한 번의 GROUP BY 쿼리로 조회 (N+1 방지)
        return quizAttemptAnswerRepository.getSelectionCountsByQuizId(quizId)
            .map { selectionCounts ->
                options.map { option ->
                    val selectionCount = selectionCounts[option.id] ?: 0
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

    /**
     * 선택한 보기 검증
     * - 모든 선택된 보기가 존재하는지 확인
     * - 선택된 보기가 해당 퀴즈에 속하는지 확인
     */
    private fun validateSelectedOptions(quizId: UUID, selectedOptionIds: List<UUID>): Mono<List<QuizOption>> {
        return quizOptionRepository.findByIdIn(selectedOptionIds)
            .collectList()
            .flatMap { selectedOptions ->
                when {
                    selectedOptions.size != selectedOptionIds.size ->
                        Mono.error(QuizException.InvalidQuizDataException("일부 보기를 찾을 수 없습니다"))

                    selectedOptions.any { it.quizId != quizId } ->
                        Mono.error(QuizException.InvalidQuizDataException("다른 퀴즈의 보기가 포함되어 있습니다"))

                    else -> Mono.just(selectedOptions)
                }
            }
    }

    /**
     * 정답 여부 확인
     * - 퀴즈의 정답 보기들과 사용자가 선택한 보기들이 정확히 일치하는지 확인
     */
    private fun checkAnswerCorrectness(quizId: UUID, selectedOptionIds: List<UUID>): Mono<Boolean> {
        return quizOptionRepository.findByQuizId(quizId)
            .filter { it.isCorrect }
            .map { it.id!! }
            .collectList()
            .map { correctOptionIds ->
                correctOptionIds.toSet() == selectedOptionIds.toSet()
            }
    }

    /**
     * 시도 및 답변 저장
     * - 다음 시도 번호 조회
     * - QuizAttempt 생성 및 저장
     * - QuizAttemptAnswer 생성 및 저장
     *
     * @return Pair<attemptId, attemptNumber>
     */
    private fun saveAttemptWithAnswers(
        quizId: UUID,
        userId: UUID,
        selectedOptionIds: List<UUID>,
        isCorrect: Boolean
    ): Mono<Pair<UUID, Int>> {
        return quizAttemptRepository.getNextAttemptNumber(quizId, userId)
            .flatMap { attemptNumber ->
                val attempt = QuizAttempt(
                    quizId = quizId,
                    userId = userId,
                    attemptNumber = attemptNumber,
                    isCorrect = isCorrect
                )

                quizAttemptRepository.save(attempt)
                    .flatMap { savedAttempt ->
                        val attemptId = savedAttempt.id!!
                        val answers = selectedOptionIds.map { optionId ->
                            QuizAttemptAnswer(attemptId = attemptId, optionId = optionId)
                        }

                        quizAttemptAnswerRepository.saveAll(answers)
                            .collectList()
                            .thenReturn(Pair(attemptId, attemptNumber))
                    }
            }
    }

    /**
     * 시도 응답 생성
     * - 최신 통계 조회
     * - QuizAttemptResponse 구성
     */
    private fun buildAttemptResponse(
        attemptId: UUID,
        quizId: UUID,
        isCorrect: Boolean,
        attemptNumber: Int,
        selectedOptionIds: List<UUID>
    ): Mono<QuizAttemptResponse> {
        val totalAttemptsMono = quizAttemptRepository.countByQuizId(quizId)
        val optionsMono = quizOptionRepository.findByQuizId(quizId).collectList()

        return Mono.zip(totalAttemptsMono, optionsMono) { totalAttempts, options ->
            calculateOptionStats(quizId, options, totalAttempts)
        }.flatMap { statsMono ->
            statsMono.map { optionStats ->
                // Add isSelected field to each option
                val optionsWithSelection = optionStats.map { option ->
                    option.copy(isSelected = selectedOptionIds.contains(UUID.fromString(option.id)))
                }

                QuizAttemptResponse(
                    attemptId = attemptId.toString(),
                    quizId = quizId.toString(),
                    isCorrect = isCorrect,
                    attemptNumber = attemptNumber,
                    options = optionsWithSelection
                )
            }
        }
    }
}
