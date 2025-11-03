package me.onetwo.growsnap.domain.analytics.repository

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.dto.UserInteraction
import me.onetwo.growsnap.jooq.generated.tables.UserContentInteractions.Companion.USER_CONTENT_INTERACTIONS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.UUID

/**
 * 사용자별 콘텐츠 인터랙션 레포지토리 구현체
 *
 * JOOQ를 사용하여 user_content_interactions 테이블을 관리합니다.
 *
 * @property dslContext JOOQ DSL Context
 */
@Repository
class UserContentInteractionRepositoryImpl(
    private val dslContext: DSLContext
) : UserContentInteractionRepository {

    override fun save(userId: UUID, contentId: UUID, interactionType: InteractionType): Mono<Void> {
        return Mono.fromCallable {
            dslContext
                .insertInto(USER_CONTENT_INTERACTIONS)
                .set(USER_CONTENT_INTERACTIONS.USER_ID, userId.toString())
                .set(USER_CONTENT_INTERACTIONS.CONTENT_ID, contentId.toString())
                .set(USER_CONTENT_INTERACTIONS.INTERACTION_TYPE, interactionType.name)
                .set(USER_CONTENT_INTERACTIONS.CREATED_AT, LocalDateTime.now())
                .set(USER_CONTENT_INTERACTIONS.CREATED_BY, userId.toString())
                .set(USER_CONTENT_INTERACTIONS.UPDATED_AT, LocalDateTime.now())
                .set(USER_CONTENT_INTERACTIONS.UPDATED_BY, userId.toString())
                .onDuplicateKeyIgnore()  // 중복 방지
                .execute()
        }.then()
    }

    override fun findContentIdsByUser(
        userId: UUID,
        interactionType: InteractionType?,
        limit: Int
    ): Flux<UUID> {
        return Flux.defer {
            val query = dslContext
                .select(USER_CONTENT_INTERACTIONS.CONTENT_ID)
                .from(USER_CONTENT_INTERACTIONS)
                .where(USER_CONTENT_INTERACTIONS.USER_ID.eq(userId.toString()))
                .and(USER_CONTENT_INTERACTIONS.DELETED_AT.isNull)

            val finalQuery = if (interactionType != null) {
                query.and(USER_CONTENT_INTERACTIONS.INTERACTION_TYPE.eq(interactionType.name))
            } else {
                query
            }

            Flux.fromIterable(
                finalQuery
                    .orderBy(USER_CONTENT_INTERACTIONS.CREATED_AT.desc())
                    .limit(limit)
                    .fetch()
                    .map { UUID.fromString(it.value1()) }
            )
        }
    }

    override fun findUsersByContent(
        contentId: UUID,
        interactionType: InteractionType?,
        limit: Int
    ): Flux<UUID> {
        return Flux.defer {
            val query = dslContext
                .select(USER_CONTENT_INTERACTIONS.USER_ID)
                .from(USER_CONTENT_INTERACTIONS)
                .where(USER_CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
                .and(USER_CONTENT_INTERACTIONS.DELETED_AT.isNull)

            val finalQuery = if (interactionType != null) {
                query.and(USER_CONTENT_INTERACTIONS.INTERACTION_TYPE.eq(interactionType.name))
            } else {
                query
            }

            Flux.fromIterable(
                finalQuery
                    .orderBy(USER_CONTENT_INTERACTIONS.CREATED_AT.desc())
                    .limit(limit)
                    .fetch()
                    .map { UUID.fromString(it.value1()) }
            )
        }
    }

    override fun findAllInteractionsByUser(
        userId: UUID,
        limit: Int
    ): Flux<UserInteraction> {
        return Flux.defer {
            Flux.fromIterable(
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
                    .fetch()
                    .map {
                        UserInteraction(
                            contentId = UUID.fromString(it.value1()),
                            interactionType = InteractionType.valueOf(it.value2()!!)
                        )
                    }
            )
        }
    }
}
