package me.onetwo.growsnap.domain.analytics.repository

import me.onetwo.growsnap.domain.content.model.ContentInteraction
import me.onetwo.growsnap.jooq.generated.tables.ContentInteractions.Companion.CONTENT_INTERACTIONS
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 콘텐츠 인터랙션 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 인터랙션 카운터를 원자적으로 증가시킵니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
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
        // JOOQ의 type-safe API로 INSERT 쿼리 생성
        return Mono.from(
            dslContext
                .insertInto(CONTENT_INTERACTIONS)
                .set(CONTENT_INTERACTIONS.CONTENT_ID, contentInteraction.contentId.toString())
                .set(CONTENT_INTERACTIONS.LIKE_COUNT, contentInteraction.likeCount)
                .set(CONTENT_INTERACTIONS.COMMENT_COUNT, contentInteraction.commentCount)
                .set(CONTENT_INTERACTIONS.SAVE_COUNT, contentInteraction.saveCount)
                .set(CONTENT_INTERACTIONS.SHARE_COUNT, contentInteraction.shareCount)
                .set(CONTENT_INTERACTIONS.VIEW_COUNT, contentInteraction.viewCount)
                .set(CONTENT_INTERACTIONS.CREATED_AT, contentInteraction.createdAt)
                .set(CONTENT_INTERACTIONS.CREATED_BY, contentInteraction.createdBy?.toString())
                .set(CONTENT_INTERACTIONS.UPDATED_AT, contentInteraction.updatedAt)
                .set(CONTENT_INTERACTIONS.UPDATED_BY, contentInteraction.updatedBy?.toString())
        ).then()
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
     * 공유 수 감소
     *
     * share_count를 1 감소시키고, updated_at을 갱신합니다.
     * 0 미만으로 내려가지 않도록 보장합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 업데이트 완료 신호
     */
    override fun decrementShareCount(contentId: UUID): Mono<Void> {
        return decrementCount(contentId, CONTENT_INTERACTIONS.SHARE_COUNT)
    }

    /**
     * 좋아요 수 조회
     *
     * @param contentId 콘텐츠 ID
     * @return 좋아요 수
     */
    override fun getLikeCount(contentId: UUID): Mono<Int> {
        return getCount(contentId, CONTENT_INTERACTIONS.LIKE_COUNT)
    }

    /**
     * 저장 수 조회
     *
     * @param contentId 콘텐츠 ID
     * @return 저장 수
     */
    override fun getSaveCount(contentId: UUID): Mono<Int> {
        return getCount(contentId, CONTENT_INTERACTIONS.SAVE_COUNT)
    }

    /**
     * 공유 수 조회
     *
     * @param contentId 콘텐츠 ID
     * @return 공유 수
     */
    override fun getShareCount(contentId: UUID): Mono<Int> {
        return getCount(contentId, CONTENT_INTERACTIONS.SHARE_COUNT)
    }

    /**
     * 댓글 수 조회
     *
     * @param contentId 콘텐츠 ID
     * @return 댓글 수
     */
    override fun getCommentCount(contentId: UUID): Mono<Int> {
        return getCount(contentId, CONTENT_INTERACTIONS.COMMENT_COUNT)
    }

    /**
     * 조회수 조회
     *
     * @param contentId 콘텐츠 ID
     * @return 조회수
     */
    override fun getViewCount(contentId: UUID): Mono<Int> {
        return getCount(contentId, CONTENT_INTERACTIONS.VIEW_COUNT)
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
    private fun incrementCount(contentId: UUID, field: Field<Int?>): Mono<Void> {
        // JOOQ의 type-safe API로 UPDATE 쿼리 생성
        return Mono.from(
            dslContext
                .update(CONTENT_INTERACTIONS)
                .set(field, DSL.field("{0} + 1", Int::class.java, field))
                .set(CONTENT_INTERACTIONS.UPDATED_AT, Instant.now())
                .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_INTERACTIONS.DELETED_AT.isNull)
        ).then()
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
    private fun decrementCount(contentId: UUID, field: Field<Int?>): Mono<Void> {
        // JOOQ의 type-safe API로 UPDATE 쿼리 생성
        return Mono.from(
            dslContext
                .update(CONTENT_INTERACTIONS)
                .set(field, DSL.field("CASE WHEN {0} > 0 THEN {0} - 1 ELSE 0 END", Int::class.java, field))
                .set(CONTENT_INTERACTIONS.UPDATED_AT, Instant.now())
                .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_INTERACTIONS.DELETED_AT.isNull)
        ).then()
    }

    /**
     * 카운트 조회 공통 로직
     *
     * 지정된 필드의 카운트 값을 조회합니다.
     * 레코드가 없거나 값이 null인 경우 0을 반환합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param field 조회할 필드
     * @return 카운트 값
     */
    private fun getCount(contentId: UUID, field: Field<Int?>): Mono<Int> {
        return Mono.from(
            dslContext
                .select(field)
                .from(CONTENT_INTERACTIONS)
                .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_INTERACTIONS.DELETED_AT.isNull)
        ).map { record -> record.getValue(field) ?: 0 }
            .defaultIfEmpty(0)
    }
}
