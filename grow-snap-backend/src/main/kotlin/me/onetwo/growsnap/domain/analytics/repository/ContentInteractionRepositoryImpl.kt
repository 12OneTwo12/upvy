package me.onetwo.growsnap.domain.analytics.repository

import me.onetwo.growsnap.domain.content.model.ContentInteraction
import me.onetwo.growsnap.jooq.generated.tables.ContentInteractions.Companion.CONTENT_INTERACTIONS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.UUID

/**
 * 콘텐츠 인터랙션 레포지토리 구현체
 *
 * JOOQ를 사용하여 content_interactions 테이블에 접근합니다.
 * 인터랙션 카운터를 원자적으로 증가시킵니다.
 *
 * @property dslContext JOOQ DSL Context
 */
@Repository
class ContentInteractionRepositoryImpl(
    private val dslContext: DSLContext
) : ContentInteractionRepository {

    /**
     * 콘텐츠 인터랙션 생성
     *
     * 새로운 콘텐츠에 대한 인터랙션 레코드를 초기화합니다.
     * 모든 카운터는 0으로 시작합니다.
     *
     * @param contentInteraction 생성할 콘텐츠 인터랙션
     * @return 생성 완료 신호
     */
    override fun create(contentInteraction: ContentInteraction): Mono<Void> {
        return Mono.fromCallable {
            dslContext.insertInto(CONTENT_INTERACTIONS)
                .set(CONTENT_INTERACTIONS.CONTENT_ID, contentInteraction.contentId)
                .set(CONTENT_INTERACTIONS.LIKE_COUNT, contentInteraction.likeCount)
                .set(CONTENT_INTERACTIONS.COMMENT_COUNT, contentInteraction.commentCount)
                .set(CONTENT_INTERACTIONS.SAVE_COUNT, contentInteraction.saveCount)
                .set(CONTENT_INTERACTIONS.SHARE_COUNT, contentInteraction.shareCount)
                .set(CONTENT_INTERACTIONS.VIEW_COUNT, contentInteraction.viewCount)
                .set(CONTENT_INTERACTIONS.CREATED_AT, contentInteraction.createdAt)
                .set(CONTENT_INTERACTIONS.CREATED_BY, contentInteraction.createdBy)
                .set(CONTENT_INTERACTIONS.UPDATED_AT, contentInteraction.updatedAt)
                .set(CONTENT_INTERACTIONS.UPDATED_BY, contentInteraction.updatedBy)
                .execute()
        }.then()
    }

    /**
     * 조회수 증가
     *
     * view_count를 1 증가시키고, updated_at을 갱신합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 업데이트 완료 신호
     */
    override fun incrementViewCount(contentId: UUID): Mono<Void> {
        return incrementCount(contentId, CONTENT_INTERACTIONS.VIEW_COUNT)
    }

    /**
     * 좋아요 수 증가
     *
     * like_count를 1 증가시키고, updated_at을 갱신합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 업데이트 완료 신호
     */
    override fun incrementLikeCount(contentId: UUID): Mono<Void> {
        return incrementCount(contentId, CONTENT_INTERACTIONS.LIKE_COUNT)
    }

    /**
     * 저장 수 증가
     *
     * save_count를 1 증가시키고, updated_at을 갱신합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 업데이트 완료 신호
     */
    override fun incrementSaveCount(contentId: UUID): Mono<Void> {
        return incrementCount(contentId, CONTENT_INTERACTIONS.SAVE_COUNT)
    }

    /**
     * 공유 수 증가
     *
     * share_count를 1 증가시키고, updated_at을 갱신합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 업데이트 완료 신호
     */
    override fun incrementShareCount(contentId: UUID): Mono<Void> {
        return incrementCount(contentId, CONTENT_INTERACTIONS.SHARE_COUNT)
    }

    /**
     * 댓글 수 증가
     *
     * comment_count를 1 증가시키고, updated_at을 갱신합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 업데이트 완료 신호
     */
    override fun incrementCommentCount(contentId: UUID): Mono<Void> {
        return incrementCount(contentId, CONTENT_INTERACTIONS.COMMENT_COUNT)
    }

    /**
     * 좋아요 수 감소
     *
     * like_count를 1 감소시키고, updated_at을 갱신합니다.
     * 0 미만으로 내려가지 않도록 보장합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 업데이트 완료 신호
     */
    override fun decrementLikeCount(contentId: UUID): Mono<Void> {
        return decrementCount(contentId, CONTENT_INTERACTIONS.LIKE_COUNT)
    }

    /**
     * 저장 수 감소
     *
     * save_count를 1 감소시키고, updated_at을 갱신합니다.
     * 0 미만으로 내려가지 않도록 보장합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 업데이트 완료 신호
     */
    override fun decrementSaveCount(contentId: UUID): Mono<Void> {
        return decrementCount(contentId, CONTENT_INTERACTIONS.SAVE_COUNT)
    }

    /**
     * 댓글 수 감소
     *
     * comment_count를 1 감소시키고, updated_at을 갱신합니다.
     * 0 미만으로 내려가지 않도록 보장합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 업데이트 완료 신호
     */
    override fun decrementCommentCount(contentId: UUID): Mono<Void> {
        return decrementCount(contentId, CONTENT_INTERACTIONS.COMMENT_COUNT)
    }

    /**
     * 좋아요 수 조회
     *
     * @param contentId 콘텐츠 ID
     * @return 좋아요 수
     */
    override fun getLikeCount(contentId: UUID): Mono<Int> {
        return Mono.fromCallable {
            dslContext
                .select(CONTENT_INTERACTIONS.LIKE_COUNT)
                .from(CONTENT_INTERACTIONS)
                .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_INTERACTIONS.DELETED_AT.isNull)
                .fetchOne(CONTENT_INTERACTIONS.LIKE_COUNT) ?: 0
        }
    }

    /**
     * 저장 수 조회
     *
     * @param contentId 콘텐츠 ID
     * @return 저장 수
     */
    override fun getSaveCount(contentId: UUID): Mono<Int> {
        return Mono.fromCallable {
            dslContext
                .select(CONTENT_INTERACTIONS.SAVE_COUNT)
                .from(CONTENT_INTERACTIONS)
                .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_INTERACTIONS.DELETED_AT.isNull)
                .fetchOne(CONTENT_INTERACTIONS.SAVE_COUNT) ?: 0
        }
    }

    /**
     * 공유 수 조회
     *
     * @param contentId 콘텐츠 ID
     * @return 공유 수
     */
    override fun getShareCount(contentId: UUID): Mono<Int> {
        return Mono.fromCallable {
            dslContext
                .select(CONTENT_INTERACTIONS.SHARE_COUNT)
                .from(CONTENT_INTERACTIONS)
                .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_INTERACTIONS.DELETED_AT.isNull)
                .fetchOne(CONTENT_INTERACTIONS.SHARE_COUNT) ?: 0
        }
    }

    /**
     * 카운터 증가 공통 로직
     *
     * 지정된 필드를 1 증가시키고, updated_at을 갱신합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param field 증가시킬 필드
     * @return 업데이트 완료 신호
     */
    private fun incrementCount(contentId: UUID, field: org.jooq.Field<Int?>): Mono<Void> {
        return Mono.fromCallable {
            dslContext.update(CONTENT_INTERACTIONS)
                .set(field, field.plus(1))
                .set(CONTENT_INTERACTIONS.UPDATED_AT, LocalDateTime.now())
                .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_INTERACTIONS.DELETED_AT.isNull)
                .execute()
        }.then()
    }

    /**
     * 카운터 감소 공통 로직
     *
     * 지정된 필드를 1 감소시키고, updated_at을 갱신합니다.
     * 0 미만으로 내려가지 않도록 보장합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param field 감소시킬 필드
     * @return 업데이트 완료 신호
     */
    private fun decrementCount(contentId: UUID, field: org.jooq.Field<Int?>): Mono<Void> {
        return Mono.fromCallable {
            dslContext.update(CONTENT_INTERACTIONS)
                .set(field, org.jooq.impl.DSL.case_()
                    .`when`(field.greaterThan(0), field.minus(1))
                    .otherwise(0))
                .set(CONTENT_INTERACTIONS.UPDATED_AT, LocalDateTime.now())
                .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_INTERACTIONS.DELETED_AT.isNull)
                .execute()
        }.then()
    }
}
