package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.quiz.model.QuizOption
import me.onetwo.upvy.jooq.generated.tables.references.QUIZ_OPTIONS
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 퀴즈 보기 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class QuizOptionRepositoryImpl(
    private val dslContext: DSLContext
) : QuizOptionRepository {

    private val logger = LoggerFactory.getLogger(QuizOptionRepositoryImpl::class.java)

    override fun save(option: QuizOption): Mono<QuizOption> {
        val optionId = option.id ?: UUID.randomUUID()
        val now = Instant.now()

        return Mono.from(
            dslContext
                .insertInto(QUIZ_OPTIONS)
                .set(QUIZ_OPTIONS.ID, optionId.toString())
                .set(QUIZ_OPTIONS.QUIZ_ID, option.quizId.toString())
                .set(QUIZ_OPTIONS.OPTION_TEXT, option.optionText)
                .set(QUIZ_OPTIONS.IS_CORRECT, option.isCorrect)
                .set(QUIZ_OPTIONS.DISPLAY_ORDER, option.displayOrder)
                .set(QUIZ_OPTIONS.CREATED_AT, now)
                .set(QUIZ_OPTIONS.CREATED_BY, option.createdBy)
                .set(QUIZ_OPTIONS.UPDATED_AT, now)
                .set(QUIZ_OPTIONS.UPDATED_BY, option.updatedBy)
                .returningResult(QUIZ_OPTIONS.ID)
        ).map {
            logger.debug("QuizOption saved: optionId=$optionId, quizId=${option.quizId}")
            option.copy(id = optionId, createdAt = now, updatedAt = now)
        }
    }

    override fun findByQuizId(quizId: UUID): Flux<QuizOption> {
        return Flux.from(
            dslContext
                .select(
                    QUIZ_OPTIONS.ID,
                    QUIZ_OPTIONS.QUIZ_ID,
                    QUIZ_OPTIONS.OPTION_TEXT,
                    QUIZ_OPTIONS.IS_CORRECT,
                    QUIZ_OPTIONS.DISPLAY_ORDER,
                    QUIZ_OPTIONS.CREATED_AT,
                    QUIZ_OPTIONS.CREATED_BY,
                    QUIZ_OPTIONS.UPDATED_AT,
                    QUIZ_OPTIONS.UPDATED_BY,
                    QUIZ_OPTIONS.DELETED_AT
                )
                .from(QUIZ_OPTIONS)
                .where(QUIZ_OPTIONS.QUIZ_ID.eq(quizId.toString()))
                .and(QUIZ_OPTIONS.DELETED_AT.isNull)
                .orderBy(QUIZ_OPTIONS.DISPLAY_ORDER.asc())
        ).map { record ->
            QuizOption(
                id = UUID.fromString(record.getValue(QUIZ_OPTIONS.ID)),
                quizId = UUID.fromString(record.getValue(QUIZ_OPTIONS.QUIZ_ID)),
                optionText = record.getValue(QUIZ_OPTIONS.OPTION_TEXT)!!,
                isCorrect = record.getValue(QUIZ_OPTIONS.IS_CORRECT)!!,
                displayOrder = record.getValue(QUIZ_OPTIONS.DISPLAY_ORDER)!!,
                createdAt = record.getValue(QUIZ_OPTIONS.CREATED_AT)!!,
                createdBy = record.getValue(QUIZ_OPTIONS.CREATED_BY),
                updatedAt = record.getValue(QUIZ_OPTIONS.UPDATED_AT)!!,
                updatedBy = record.getValue(QUIZ_OPTIONS.UPDATED_BY),
                deletedAt = record.getValue(QUIZ_OPTIONS.DELETED_AT)
            )
        }
    }

    override fun findById(optionId: UUID): Mono<QuizOption> {
        return Mono.from(
            dslContext
                .select(
                    QUIZ_OPTIONS.ID,
                    QUIZ_OPTIONS.QUIZ_ID,
                    QUIZ_OPTIONS.OPTION_TEXT,
                    QUIZ_OPTIONS.IS_CORRECT,
                    QUIZ_OPTIONS.DISPLAY_ORDER,
                    QUIZ_OPTIONS.CREATED_AT,
                    QUIZ_OPTIONS.CREATED_BY,
                    QUIZ_OPTIONS.UPDATED_AT,
                    QUIZ_OPTIONS.UPDATED_BY,
                    QUIZ_OPTIONS.DELETED_AT
                )
                .from(QUIZ_OPTIONS)
                .where(QUIZ_OPTIONS.ID.eq(optionId.toString()))
                .and(QUIZ_OPTIONS.DELETED_AT.isNull)
        ).map { record ->
            QuizOption(
                id = UUID.fromString(record.getValue(QUIZ_OPTIONS.ID)),
                quizId = UUID.fromString(record.getValue(QUIZ_OPTIONS.QUIZ_ID)),
                optionText = record.getValue(QUIZ_OPTIONS.OPTION_TEXT)!!,
                isCorrect = record.getValue(QUIZ_OPTIONS.IS_CORRECT)!!,
                displayOrder = record.getValue(QUIZ_OPTIONS.DISPLAY_ORDER)!!,
                createdAt = record.getValue(QUIZ_OPTIONS.CREATED_AT)!!,
                createdBy = record.getValue(QUIZ_OPTIONS.CREATED_BY),
                updatedAt = record.getValue(QUIZ_OPTIONS.UPDATED_AT)!!,
                updatedBy = record.getValue(QUIZ_OPTIONS.UPDATED_BY),
                deletedAt = record.getValue(QUIZ_OPTIONS.DELETED_AT)
            )
        }
    }

    override fun findByIdIn(optionIds: List<UUID>): Flux<QuizOption> {
        if (optionIds.isEmpty()) {
            return Flux.empty()
        }

        val stringIds = optionIds.map { it.toString() }

        return Flux.from(
            dslContext
                .select(
                    QUIZ_OPTIONS.ID,
                    QUIZ_OPTIONS.QUIZ_ID,
                    QUIZ_OPTIONS.OPTION_TEXT,
                    QUIZ_OPTIONS.IS_CORRECT,
                    QUIZ_OPTIONS.DISPLAY_ORDER,
                    QUIZ_OPTIONS.CREATED_AT,
                    QUIZ_OPTIONS.CREATED_BY,
                    QUIZ_OPTIONS.UPDATED_AT,
                    QUIZ_OPTIONS.UPDATED_BY,
                    QUIZ_OPTIONS.DELETED_AT
                )
                .from(QUIZ_OPTIONS)
                .where(QUIZ_OPTIONS.ID.`in`(stringIds))
                .and(QUIZ_OPTIONS.DELETED_AT.isNull)
        ).map { record ->
            QuizOption(
                id = UUID.fromString(record.getValue(QUIZ_OPTIONS.ID)),
                quizId = UUID.fromString(record.getValue(QUIZ_OPTIONS.QUIZ_ID)),
                optionText = record.getValue(QUIZ_OPTIONS.OPTION_TEXT)!!,
                isCorrect = record.getValue(QUIZ_OPTIONS.IS_CORRECT)!!,
                displayOrder = record.getValue(QUIZ_OPTIONS.DISPLAY_ORDER)!!,
                createdAt = record.getValue(QUIZ_OPTIONS.CREATED_AT)!!,
                createdBy = record.getValue(QUIZ_OPTIONS.CREATED_BY),
                updatedAt = record.getValue(QUIZ_OPTIONS.UPDATED_AT)!!,
                updatedBy = record.getValue(QUIZ_OPTIONS.UPDATED_BY),
                deletedAt = record.getValue(QUIZ_OPTIONS.DELETED_AT)
            )
        }
    }

    override fun deleteByQuizId(quizId: UUID, deletedBy: UUID): Mono<Void> {
        val now = Instant.now()

        return Mono.from(
            dslContext
                .update(QUIZ_OPTIONS)
                .set(QUIZ_OPTIONS.DELETED_AT, now)
                .set(QUIZ_OPTIONS.UPDATED_AT, now)
                .set(QUIZ_OPTIONS.UPDATED_BY, deletedBy.toString())
                .where(QUIZ_OPTIONS.QUIZ_ID.eq(quizId.toString()))
                .and(QUIZ_OPTIONS.DELETED_AT.isNull)
        ).then()
    }

    override fun countByQuizId(quizId: UUID): Mono<Int> {
        return Mono.from(
            dslContext
                .selectCount()
                .from(QUIZ_OPTIONS)
                .where(QUIZ_OPTIONS.QUIZ_ID.eq(quizId.toString()))
                .and(QUIZ_OPTIONS.DELETED_AT.isNull)
        ).map { record ->
            record.value1() ?: 0
        }.defaultIfEmpty(0)
    }
}
