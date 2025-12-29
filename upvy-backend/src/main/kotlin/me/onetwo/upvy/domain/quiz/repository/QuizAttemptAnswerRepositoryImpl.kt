package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.quiz.model.QuizAttemptAnswer
import me.onetwo.upvy.jooq.generated.tables.references.QUIZ_ATTEMPT_ANSWERS
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
 * 퀴즈 시도 답변 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class QuizAttemptAnswerRepositoryImpl(
    private val dslContext: DSLContext
) : QuizAttemptAnswerRepository {

    private val logger = LoggerFactory.getLogger(QuizAttemptAnswerRepositoryImpl::class.java)

    override fun save(answer: QuizAttemptAnswer): Mono<QuizAttemptAnswer> {
        val answerId = answer.id ?: UUID.randomUUID()
        val now = Instant.now()

        return Mono.from(
            dslContext
                .insertInto(QUIZ_ATTEMPT_ANSWERS)
                .set(QUIZ_ATTEMPT_ANSWERS.ID, answerId.toString())
                .set(QUIZ_ATTEMPT_ANSWERS.ATTEMPT_ID, answer.attemptId.toString())
                .set(QUIZ_ATTEMPT_ANSWERS.OPTION_ID, answer.optionId.toString())
                .set(QUIZ_ATTEMPT_ANSWERS.CREATED_AT, now)
                .returningResult(QUIZ_ATTEMPT_ANSWERS.ID)
        ).map {
            logger.debug("QuizAttemptAnswer saved: answerId=$answerId, attemptId=${answer.attemptId}, optionId=${answer.optionId}")
            answer.copy(id = answerId, createdAt = now)
        }
    }

    override fun saveAll(answers: List<QuizAttemptAnswer>): Flux<QuizAttemptAnswer> {
        if (answers.isEmpty()) {
            return Flux.empty()
        }

        val now = Instant.now()
        val answersWithIds = answers.map { it.copy(id = it.id ?: UUID.randomUUID(), createdAt = now) }

        val queries = answersWithIds.map { answer ->
            dslContext.insertInto(QUIZ_ATTEMPT_ANSWERS)
                .set(QUIZ_ATTEMPT_ANSWERS.ID, answer.id.toString())
                .set(QUIZ_ATTEMPT_ANSWERS.ATTEMPT_ID, answer.attemptId.toString())
                .set(QUIZ_ATTEMPT_ANSWERS.OPTION_ID, answer.optionId.toString())
                .set(QUIZ_ATTEMPT_ANSWERS.CREATED_AT, answer.createdAt)
        }

        return Flux.from(dslContext.batch(queries))
            .doOnComplete { logger.debug("Batch inserted ${answersWithIds.size} QuizAttemptAnswers") }
            .thenMany(Flux.fromIterable(answersWithIds))
    }

    override fun findByAttemptId(attemptId: UUID): Flux<QuizAttemptAnswer> {
        return Flux.from(
            dslContext
                .select(
                    QUIZ_ATTEMPT_ANSWERS.ID,
                    QUIZ_ATTEMPT_ANSWERS.ATTEMPT_ID,
                    QUIZ_ATTEMPT_ANSWERS.OPTION_ID,
                    QUIZ_ATTEMPT_ANSWERS.CREATED_AT
                )
                .from(QUIZ_ATTEMPT_ANSWERS)
                .where(QUIZ_ATTEMPT_ANSWERS.ATTEMPT_ID.eq(attemptId.toString()))
        ).map { record ->
            QuizAttemptAnswer(
                id = UUID.fromString(record.getValue(QUIZ_ATTEMPT_ANSWERS.ID)),
                attemptId = UUID.fromString(record.getValue(QUIZ_ATTEMPT_ANSWERS.ATTEMPT_ID)),
                optionId = UUID.fromString(record.getValue(QUIZ_ATTEMPT_ANSWERS.OPTION_ID)),
                createdAt = record.getValue(QUIZ_ATTEMPT_ANSWERS.CREATED_AT)!!
            )
        }
    }

    override fun countByAttemptId(attemptId: UUID): Mono<Int> {
        return Mono.from(
            dslContext
                .selectCount()
                .from(QUIZ_ATTEMPT_ANSWERS)
                .where(QUIZ_ATTEMPT_ANSWERS.ATTEMPT_ID.eq(attemptId.toString()))
        ).map { record ->
            record.value1() ?: 0
        }.defaultIfEmpty(0)
    }

    override fun deleteByAttemptId(attemptId: UUID): Mono<Void> {
        return Mono.from(
            dslContext
                .deleteFrom(QUIZ_ATTEMPT_ANSWERS)
                .where(QUIZ_ATTEMPT_ANSWERS.ATTEMPT_ID.eq(attemptId.toString()))
        ).then()
    }

    override fun getSelectionCountsByQuizId(quizId: UUID): Mono<Map<UUID, Int>> {
        return Flux.from(
            dslContext
                .select(
                    QUIZ_ATTEMPT_ANSWERS.OPTION_ID,
                    DSL.count(QUIZ_ATTEMPT_ANSWERS.ID).`as`("selection_count")
                )
                .from(QUIZ_ATTEMPT_ANSWERS)
                .join(QUIZ_ATTEMPTS)
                .on(QUIZ_ATTEMPT_ANSWERS.ATTEMPT_ID.eq(QUIZ_ATTEMPTS.ID))
                .where(QUIZ_ATTEMPTS.QUIZ_ID.eq(quizId.toString()))
                .groupBy(QUIZ_ATTEMPT_ANSWERS.OPTION_ID)
        ).collectMap(
            { record -> UUID.fromString(record.getValue(QUIZ_ATTEMPT_ANSWERS.OPTION_ID)) },
            { record -> record.get("selection_count", Int::class.java) ?: 0 }
        ).defaultIfEmpty(emptyMap())
    }
}
