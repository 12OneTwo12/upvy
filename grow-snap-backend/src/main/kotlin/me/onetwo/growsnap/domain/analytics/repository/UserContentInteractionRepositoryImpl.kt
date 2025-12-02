package me.onetwo.growsnap.domain.analytics.repository

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.dto.UserInteraction
import me.onetwo.growsnap.jooq.generated.tables.UserContentInteractions.Companion.USER_CONTENT_INTERACTIONS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 사용자별 콘텐츠 인터랙션 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class UserContentInteractionRepositoryImpl(
    private val dslContext: DSLContext
) : UserContentInteractionRepository {

    override fun save(userId: UUID, contentId: UUID, interactionType: InteractionType): Mono<Void> {
        val now = Instant.now()

        // JOOQ의 type-safe API로 INSERT 쿼리 생성
        return Mono.from(
            dslContext
                .insertInto(USER_CONTENT_INTERACTIONS)
                .set(USER_CONTENT_INTERACTIONS.USER_ID, userId.toString())
                .set(USER_CONTENT_INTERACTIONS.CONTENT_ID, contentId.toString())
                .set(USER_CONTENT_INTERACTIONS.INTERACTION_TYPE, interactionType.name)
                .set(USER_CONTENT_INTERACTIONS.CREATED_AT, now)
                .set(USER_CONTENT_INTERACTIONS.CREATED_BY, userId.toString())
                .set(USER_CONTENT_INTERACTIONS.UPDATED_AT, now)
                .set(USER_CONTENT_INTERACTIONS.UPDATED_BY, userId.toString())
                .onDuplicateKeyIgnore()  // 중복 방지
        ).then()
    }

    override fun findContentIdsByUser(
        userId: UUID,
        interactionType: InteractionType?,
        limit: Int
    ): Flux<UUID> {
        var query = dslContext
            .select(USER_CONTENT_INTERACTIONS.CONTENT_ID)
            .from(USER_CONTENT_INTERACTIONS)
            .where(USER_CONTENT_INTERACTIONS.USER_ID.eq(userId.toString()))
            .and(USER_CONTENT_INTERACTIONS.DELETED_AT.isNull)

        if (interactionType != null) {
            query = query.and(USER_CONTENT_INTERACTIONS.INTERACTION_TYPE.eq(interactionType.name))
        }

        // JOOQ의 type-safe API로 SELECT 쿼리 생성
        return Flux.from(
            query
                .orderBy(USER_CONTENT_INTERACTIONS.CREATED_AT.desc())
                .limit(limit)
        ).map { record -> UUID.fromString(record.value1()) }
    }

    override fun findUsersByContent(
        contentId: UUID,
        interactionType: InteractionType?,
        limit: Int
    ): Flux<UUID> {
        var query = dslContext
            .select(USER_CONTENT_INTERACTIONS.USER_ID)
            .from(USER_CONTENT_INTERACTIONS)
            .where(USER_CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
            .and(USER_CONTENT_INTERACTIONS.DELETED_AT.isNull)

        if (interactionType != null) {
            query = query.and(USER_CONTENT_INTERACTIONS.INTERACTION_TYPE.eq(interactionType.name))
        }

        // JOOQ의 type-safe API로 SELECT 쿼리 생성
        return Flux.from(
            query
                .orderBy(USER_CONTENT_INTERACTIONS.CREATED_AT.desc())
                .limit(limit)
        ).map { record -> UUID.fromString(record.value1()) }
    }

    override fun findAllInteractionsByUser(
        userId: UUID,
        limit: Int
    ): Flux<UserInteraction> {
        // JOOQ의 type-safe API로 SELECT 쿼리 생성
        return Flux.from(
            dslContext
                .select(
                    USER_CONTENT_INTERACTIONS.CONTENT_ID,
                    USER_CONTENT_INTERACTIONS.INTERACTION_TYPE
                )
                .from(USER_CONTENT_INTERACTIONS)
                .where(USER_CONTENT_INTERACTIONS.USER_ID.eq(userId.toString()))
                .and(USER_CONTENT_INTERACTIONS.DELETED_AT.isNull)
                .orderBy(USER_CONTENT_INTERACTIONS.CREATED_AT.desc())
                .limit(limit)
        ).map { record ->
            UserInteraction(
                contentId = UUID.fromString(record.value1()),
                interactionType = InteractionType.valueOf(record.value2()!!)
            )
        }
    }
}
