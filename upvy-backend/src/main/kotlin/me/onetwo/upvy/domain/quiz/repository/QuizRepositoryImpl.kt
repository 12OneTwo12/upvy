package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.quiz.dto.QuizMetadataResponse
import me.onetwo.upvy.domain.quiz.exception.QuizException.QuizNotFoundException
import me.onetwo.upvy.domain.quiz.model.Quiz
import me.onetwo.upvy.jooq.generated.tables.references.QUIZZES
import me.onetwo.upvy.jooq.generated.tables.references.QUIZ_ATTEMPTS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 퀴즈 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class QuizRepositoryImpl(
    private val dslContext: DSLContext
) : QuizRepository {

    private val logger = LoggerFactory.getLogger(QuizRepositoryImpl::class.java)

    override fun save(quiz: Quiz): Mono<Quiz> {
        val quizId = quiz.id ?: UUID.randomUUID()
        val now = Instant.now()

        return Mono.from(
            dslContext
                .insertInto(QUIZZES)
                .set(QUIZZES.ID, quizId.toString())
                .set(QUIZZES.CONTENT_ID, quiz.contentId.toString())
                .set(QUIZZES.QUESTION, quiz.question)
                .set(QUIZZES.ALLOW_MULTIPLE_ANSWERS, quiz.allowMultipleAnswers)
                .set(QUIZZES.CREATED_AT, now)
                .set(QUIZZES.CREATED_BY, quiz.createdBy)
                .set(QUIZZES.UPDATED_AT, now)
                .set(QUIZZES.UPDATED_BY, quiz.updatedBy)
                .returningResult(QUIZZES.ID)
        ).map {
            logger.debug("Quiz saved: quizId=$quizId, contentId=${quiz.contentId}")
            quiz.copy(id = quizId, createdAt = now, updatedAt = now)
        }
    }

    override fun findById(quizId: UUID): Mono<Quiz> {
        return Mono.from(
            dslContext
                .select(
                    QUIZZES.ID,
                    QUIZZES.CONTENT_ID,
                    QUIZZES.QUESTION,
                    QUIZZES.ALLOW_MULTIPLE_ANSWERS,
                    QUIZZES.CREATED_AT,
                    QUIZZES.CREATED_BY,
                    QUIZZES.UPDATED_AT,
                    QUIZZES.UPDATED_BY,
                    QUIZZES.DELETED_AT
                )
                .from(QUIZZES)
                .where(QUIZZES.ID.eq(quizId.toString()))
                .and(QUIZZES.DELETED_AT.isNull)
        ).map { record ->
            Quiz(
                id = UUID.fromString(record.getValue(QUIZZES.ID)),
                contentId = UUID.fromString(record.getValue(QUIZZES.CONTENT_ID)),
                question = record.getValue(QUIZZES.QUESTION)!!,
                allowMultipleAnswers = record.getValue(QUIZZES.ALLOW_MULTIPLE_ANSWERS)!!,
                createdAt = record.getValue(QUIZZES.CREATED_AT)!!,
                createdBy = record.getValue(QUIZZES.CREATED_BY),
                updatedAt = record.getValue(QUIZZES.UPDATED_AT)!!,
                updatedBy = record.getValue(QUIZZES.UPDATED_BY),
                deletedAt = record.getValue(QUIZZES.DELETED_AT)
            )
        }.switchIfEmpty(Mono.error(QuizNotFoundException(quizId.toString())))
    }

    override fun findByContentId(contentId: UUID): Mono<Quiz> {
        return Mono.from(
            dslContext
                .select(
                    QUIZZES.ID,
                    QUIZZES.CONTENT_ID,
                    QUIZZES.QUESTION,
                    QUIZZES.ALLOW_MULTIPLE_ANSWERS,
                    QUIZZES.CREATED_AT,
                    QUIZZES.CREATED_BY,
                    QUIZZES.UPDATED_AT,
                    QUIZZES.UPDATED_BY,
                    QUIZZES.DELETED_AT
                )
                .from(QUIZZES)
                .where(QUIZZES.CONTENT_ID.eq(contentId.toString()))
                .and(QUIZZES.DELETED_AT.isNull)
        ).map { record ->
            Quiz(
                id = UUID.fromString(record.getValue(QUIZZES.ID)),
                contentId = UUID.fromString(record.getValue(QUIZZES.CONTENT_ID)),
                question = record.getValue(QUIZZES.QUESTION)!!,
                allowMultipleAnswers = record.getValue(QUIZZES.ALLOW_MULTIPLE_ANSWERS)!!,
                createdAt = record.getValue(QUIZZES.CREATED_AT)!!,
                createdBy = record.getValue(QUIZZES.CREATED_BY),
                updatedAt = record.getValue(QUIZZES.UPDATED_AT)!!,
                updatedBy = record.getValue(QUIZZES.UPDATED_BY),
                deletedAt = record.getValue(QUIZZES.DELETED_AT)
            )
        }.switchIfEmpty(Mono.error(QuizNotFoundException(contentId.toString())))
    }

    override fun update(quiz: Quiz): Mono<Quiz> {
        val now = Instant.now()
        val quizId = quiz.id ?: return Mono.error(IllegalArgumentException("Quiz ID cannot be null for update"))

        return Mono.from(
            dslContext
                .update(QUIZZES)
                .set(QUIZZES.QUESTION, quiz.question)
                .set(QUIZZES.ALLOW_MULTIPLE_ANSWERS, quiz.allowMultipleAnswers)
                .set(QUIZZES.UPDATED_AT, now)
                .set(QUIZZES.UPDATED_BY, quiz.updatedBy)
                .where(QUIZZES.ID.eq(quizId.toString()))
                .and(QUIZZES.DELETED_AT.isNull)
        ).flatMap { rowsAffected: Any ->
            val hasUpdated = when (rowsAffected) {
                is Long -> rowsAffected > 0L
                is Int -> rowsAffected > 0
                else -> rowsAffected.toString().toLongOrNull()?.let { it > 0L } ?: false
            }

            if (hasUpdated) {
                findById(quizId) // 업데이트 후 최신 데이터를 다시 조회하여 반환
            } else {
                Mono.error(QuizNotFoundException(quizId.toString()))
            }
        }
    }

    override fun delete(quizId: UUID, deletedBy: UUID): Mono<Void> {
        val now = Instant.now()

        return Mono.from(
            dslContext
                .update(QUIZZES)
                .set(QUIZZES.DELETED_AT, now)
                .set(QUIZZES.UPDATED_AT, now)
                .set(QUIZZES.UPDATED_BY, deletedBy.toString())
                .where(QUIZZES.ID.eq(quizId.toString()))
                .and(QUIZZES.DELETED_AT.isNull)
        ).then()
    }

    override fun existsByContentId(contentId: UUID): Mono<Boolean> {
        return Mono.from(
            dslContext
                .selectCount()
                .from(QUIZZES)
                .where(QUIZZES.CONTENT_ID.eq(contentId.toString()))
                .and(QUIZZES.DELETED_AT.isNull)
        ).map { record ->
            val count = record.value1() ?: 0
            count > 0
        }.defaultIfEmpty(false)
    }

    override fun findQuizMetadataByContentIds(contentIds: List<UUID>, userId: UUID?): Mono<Map<UUID, QuizMetadataResponse>> {
        // contentIds가 비어있으면 빈 Map 반환
        if (contentIds.isEmpty()) {
            return Mono.just(emptyMap())
        }

        // 1. 퀴즈 조회 (contentId → Quiz 매핑)
        val quizzesMono: Mono<Map<UUID, Quiz>> = Flux.from(
            dslContext
                .select(
                    QUIZZES.ID,
                    QUIZZES.CONTENT_ID
                )
                .from(QUIZZES)
                .where(QUIZZES.CONTENT_ID.`in`(contentIds.map { it.toString() }))
                .and(QUIZZES.DELETED_AT.isNull)
        ).collectMap(
            { record -> UUID.fromString(record.getValue(QUIZZES.CONTENT_ID)) }, // contentId를 키로
            { record ->
                Quiz(
                    id = UUID.fromString(record.getValue(QUIZZES.ID)),
                    contentId = UUID.fromString(record.getValue(QUIZZES.CONTENT_ID)),
                    question = "",
                    allowMultipleAnswers = false,
                    createdAt = Instant.now(),
                    createdBy = null,
                    updatedAt = Instant.now(),
                    updatedBy = null,
                    deletedAt = null
                )
            }
        )

        // 2. 사용자의 시도 횟수 조회 (quizId → attemptCount 매핑)
        val attemptCountsMono: Mono<Map<UUID, Int>> = if (userId != null) {
            quizzesMono.flatMap { quizzesMap ->
                if (quizzesMap.isEmpty()) {
                    return@flatMap Mono.just(emptyMap<UUID, Int>())
                }

                val quizIds = quizzesMap.values.mapNotNull { it.id }
                Flux.from(
                    dslContext
                        .select(
                            QUIZ_ATTEMPTS.QUIZ_ID,
                            DSL.count().`as`("attempt_count")
                        )
                        .from(QUIZ_ATTEMPTS)
                        .where(QUIZ_ATTEMPTS.QUIZ_ID.`in`(quizIds.map { it.toString() }))
                        .and(QUIZ_ATTEMPTS.USER_ID.eq(userId.toString()))
                        .groupBy(QUIZ_ATTEMPTS.QUIZ_ID)
                ).collectMap(
                    { record -> UUID.fromString(record.getValue(QUIZ_ATTEMPTS.QUIZ_ID)) }, // quizId를 키로
                    { record -> record.getValue("attempt_count", Int::class.java) ?: 0 }
                )
            }
        } else {
            // userId가 null이면 빈 Map
            Mono.just(emptyMap())
        }

        // 3. 두 쿼리 결과를 조합
        return Mono.zip(quizzesMono, attemptCountsMono)
            .map { tuple ->
                val quizzesMap = tuple.t1
                val attemptCountsMap = tuple.t2

                // quizId를 키로 하는 Map을 contentId를 키로 하는 Map으로 변환
                quizzesMap.mapValues { (contentId, quiz) ->
                    val quizId = quiz.id!!
                    val attemptCount = attemptCountsMap[quizId] ?: 0
                    QuizMetadataResponse(
                        quizId = quizId.toString(),
                        hasAttempted = attemptCount > 0,
                        attemptCount = attemptCount
                    )
                }
            }
            .defaultIfEmpty(emptyMap())
    }
}
