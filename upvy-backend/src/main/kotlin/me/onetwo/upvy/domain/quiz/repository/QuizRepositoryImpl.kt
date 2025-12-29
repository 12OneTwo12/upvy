package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.quiz.model.Quiz
import me.onetwo.upvy.jooq.generated.tables.references.QUIZZES
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
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
        }
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
        }
    }

    override fun update(quiz: Quiz): Mono<Quiz> {
        val now = Instant.now()

        return Mono.from(
            dslContext
                .update(QUIZZES)
                .set(QUIZZES.QUESTION, quiz.question)
                .set(QUIZZES.ALLOW_MULTIPLE_ANSWERS, quiz.allowMultipleAnswers)
                .set(QUIZZES.UPDATED_AT, now)
                .set(QUIZZES.UPDATED_BY, quiz.updatedBy)
                .where(QUIZZES.ID.eq(quiz.id.toString()))
                .and(QUIZZES.DELETED_AT.isNull)
        ).then(Mono.just(quiz.copy(updatedAt = now)))
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
}
