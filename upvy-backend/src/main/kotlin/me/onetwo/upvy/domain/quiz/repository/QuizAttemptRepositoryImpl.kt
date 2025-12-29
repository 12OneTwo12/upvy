package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.quiz.model.QuizAttempt
import me.onetwo.upvy.jooq.generated.tables.references.QUIZ_ATTEMPTS
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 퀴즈 시도 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class QuizAttemptRepositoryImpl(
    private val dslContext: DSLContext
) : QuizAttemptRepository {

    private val logger = LoggerFactory.getLogger(QuizAttemptRepositoryImpl::class.java)

    override fun save(attempt: QuizAttempt): Mono<QuizAttempt> {
        val attemptId = attempt.id ?: UUID.randomUUID()
        val now = Instant.now()

        return Mono.from(
            dslContext
                .insertInto(QUIZ_ATTEMPTS)
                .set(QUIZ_ATTEMPTS.ID, attemptId.toString())
                .set(QUIZ_ATTEMPTS.QUIZ_ID, attempt.quizId.toString())
                .set(QUIZ_ATTEMPTS.USER_ID, attempt.userId.toString())
                .set(QUIZ_ATTEMPTS.ATTEMPT_NUMBER, attempt.attemptNumber)
                .set(QUIZ_ATTEMPTS.IS_CORRECT, attempt.isCorrect)
                .set(QUIZ_ATTEMPTS.CREATED_AT, now)
                .returningResult(QUIZ_ATTEMPTS.ID)
        ).map {
            logger.debug("QuizAttempt saved: attemptId=$attemptId, quizId=${attempt.quizId}, userId=${attempt.userId}, attemptNumber=${attempt.attemptNumber}")
            attempt.copy(id = attemptId, createdAt = now)
        }
    }

    override fun findById(attemptId: UUID): Mono<QuizAttempt> {
        return Mono.from(
            dslContext
                .select(
                    QUIZ_ATTEMPTS.ID,
                    QUIZ_ATTEMPTS.QUIZ_ID,
                    QUIZ_ATTEMPTS.USER_ID,
                    QUIZ_ATTEMPTS.ATTEMPT_NUMBER,
                    QUIZ_ATTEMPTS.IS_CORRECT,
                    QUIZ_ATTEMPTS.CREATED_AT
                )
                .from(QUIZ_ATTEMPTS)
                .where(QUIZ_ATTEMPTS.ID.eq(attemptId.toString()))
        ).map { record ->
            QuizAttempt(
                id = UUID.fromString(record.getValue(QUIZ_ATTEMPTS.ID)),
                quizId = UUID.fromString(record.getValue(QUIZ_ATTEMPTS.QUIZ_ID)),
                userId = UUID.fromString(record.getValue(QUIZ_ATTEMPTS.USER_ID)),
                attemptNumber = record.getValue(QUIZ_ATTEMPTS.ATTEMPT_NUMBER)!!,
                isCorrect = record.getValue(QUIZ_ATTEMPTS.IS_CORRECT)!!,
                createdAt = record.getValue(QUIZ_ATTEMPTS.CREATED_AT)!!
            )
        }
    }

    override fun findByQuizIdAndUserId(quizId: UUID, userId: UUID): Flux<QuizAttempt> {
        return Flux.from(
            dslContext
                .select(
                    QUIZ_ATTEMPTS.ID,
                    QUIZ_ATTEMPTS.QUIZ_ID,
                    QUIZ_ATTEMPTS.USER_ID,
                    QUIZ_ATTEMPTS.ATTEMPT_NUMBER,
                    QUIZ_ATTEMPTS.IS_CORRECT,
                    QUIZ_ATTEMPTS.CREATED_AT
                )
                .from(QUIZ_ATTEMPTS)
                .where(QUIZ_ATTEMPTS.QUIZ_ID.eq(quizId.toString()))
                .and(QUIZ_ATTEMPTS.USER_ID.eq(userId.toString()))
                .orderBy(QUIZ_ATTEMPTS.CREATED_AT.desc())
        ).map { record ->
            QuizAttempt(
                id = UUID.fromString(record.getValue(QUIZ_ATTEMPTS.ID)),
                quizId = UUID.fromString(record.getValue(QUIZ_ATTEMPTS.QUIZ_ID)),
                userId = UUID.fromString(record.getValue(QUIZ_ATTEMPTS.USER_ID)),
                attemptNumber = record.getValue(QUIZ_ATTEMPTS.ATTEMPT_NUMBER)!!,
                isCorrect = record.getValue(QUIZ_ATTEMPTS.IS_CORRECT)!!,
                createdAt = record.getValue(QUIZ_ATTEMPTS.CREATED_AT)!!
            )
        }
    }

    override fun getNextAttemptNumber(quizId: UUID, userId: UUID): Mono<Int> {
        return Mono.from(
            dslContext
                .select(QUIZ_ATTEMPTS.ATTEMPT_NUMBER.max())
                .from(QUIZ_ATTEMPTS)
                .where(QUIZ_ATTEMPTS.QUIZ_ID.eq(quizId.toString()))
                .and(QUIZ_ATTEMPTS.USER_ID.eq(userId.toString()))
        ).map { record ->
            val maxAttemptNumber = record.value1() ?: 0
            maxAttemptNumber + 1
        }.defaultIfEmpty(1)
    }

    override fun findByQuizId(quizId: UUID): Flux<QuizAttempt> {
        return Flux.from(
            dslContext
                .select(
                    QUIZ_ATTEMPTS.ID,
                    QUIZ_ATTEMPTS.QUIZ_ID,
                    QUIZ_ATTEMPTS.USER_ID,
                    QUIZ_ATTEMPTS.ATTEMPT_NUMBER,
                    QUIZ_ATTEMPTS.IS_CORRECT,
                    QUIZ_ATTEMPTS.CREATED_AT
                )
                .from(QUIZ_ATTEMPTS)
                .where(QUIZ_ATTEMPTS.QUIZ_ID.eq(quizId.toString()))
                .orderBy(QUIZ_ATTEMPTS.CREATED_AT.desc())
        ).map { record ->
            QuizAttempt(
                id = UUID.fromString(record.getValue(QUIZ_ATTEMPTS.ID)),
                quizId = UUID.fromString(record.getValue(QUIZ_ATTEMPTS.QUIZ_ID)),
                userId = UUID.fromString(record.getValue(QUIZ_ATTEMPTS.USER_ID)),
                attemptNumber = record.getValue(QUIZ_ATTEMPTS.ATTEMPT_NUMBER)!!,
                isCorrect = record.getValue(QUIZ_ATTEMPTS.IS_CORRECT)!!,
                createdAt = record.getValue(QUIZ_ATTEMPTS.CREATED_AT)!!
            )
        }
    }

    override fun countByQuizIdAndUserId(quizId: UUID, userId: UUID): Mono<Int> {
        return Mono.from(
            dslContext
                .selectCount()
                .from(QUIZ_ATTEMPTS)
                .where(QUIZ_ATTEMPTS.QUIZ_ID.eq(quizId.toString()))
                .and(QUIZ_ATTEMPTS.USER_ID.eq(userId.toString()))
        ).map { record ->
            record.value1() ?: 0
        }.defaultIfEmpty(0)
    }
}
