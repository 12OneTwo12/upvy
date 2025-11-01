package me.onetwo.growsnap.domain.feed.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.feed.dto.CreatorInfoResponse
import me.onetwo.growsnap.domain.feed.dto.FeedItemResponse
import me.onetwo.growsnap.domain.feed.dto.InteractionInfoResponse
import me.onetwo.growsnap.domain.feed.dto.SubtitleInfoResponse
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENTS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_INTERACTIONS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_METADATA
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_SUBTITLES
import me.onetwo.growsnap.jooq.generated.tables.references.FOLLOWS
import me.onetwo.growsnap.jooq.generated.tables.references.USERS
import me.onetwo.growsnap.jooq.generated.tables.references.USER_LIKES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_PROFILES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_SAVES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 피드 레포지토리 구현체
 *
 * JOOQ를 사용하여 피드 데이터를 조회합니다.
 *
 * @property dslContext JOOQ DSL Context
 * @property objectMapper JSON 파싱용 ObjectMapper
 */
@Repository
class FeedRepositoryImpl(
    private val dslContext: DSLContext,
    private val objectMapper: ObjectMapper,
    private val contentPhotoRepository: me.onetwo.growsnap.domain.content.repository.ContentPhotoRepository
) : FeedRepository {

    /**
     * 메인 피드 조회
     *
     * 추천 알고리즘 기반으로 사용자에게 맞춤화된 피드를 제공합니다.
     * (현재는 최신 콘텐츠 기반, 향후 추천 알고리즘 추가)
     *
     * @param userId 사용자 ID
     * @param cursor 커서 (마지막 조회 콘텐츠 ID, null이면 첫 페이지)
     * @param limit 조회할 항목 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @return 피드 아이템 목록
     */
    override fun findMainFeed(
        userId: UUID,
        cursor: UUID?,
        limit: Int,
        excludeContentIds: List<UUID>
    ): Flux<FeedItemResponse> {
        return Mono.fromCallable {
            var query = dslContext
                .select(
                    // CONTENTS 필요 컬럼만 명시적으로 선택
                    CONTENTS.ID,
                    CONTENTS.CONTENT_TYPE,
                    CONTENTS.URL,
                    CONTENTS.THUMBNAIL_URL,
                    CONTENTS.DURATION,
                    CONTENTS.WIDTH,
                    CONTENTS.HEIGHT,
                    CONTENTS.CREATED_AT,
                    // CONTENT_METADATA 필요 컬럼만 명시적으로 선택
                    CONTENT_METADATA.TITLE,
                    CONTENT_METADATA.DESCRIPTION,
                    CONTENT_METADATA.CATEGORY,
                    CONTENT_METADATA.TAGS,
                    // CONTENT_INTERACTIONS 필요 컬럼만 명시적으로 선택
                    CONTENT_INTERACTIONS.LIKE_COUNT,
                    CONTENT_INTERACTIONS.COMMENT_COUNT,
                    CONTENT_INTERACTIONS.SAVE_COUNT,
                    CONTENT_INTERACTIONS.SHARE_COUNT,
                    CONTENT_INTERACTIONS.VIEW_COUNT,
                    // USERS, USER_PROFILES
                    USERS.ID,
                    USER_PROFILES.NICKNAME,
                    USER_PROFILES.PROFILE_IMAGE_URL,
                    USER_PROFILES.FOLLOWER_COUNT,
                    // USER_LIKES, USER_SAVES (사용자별 상태)
                    DSL.field(USER_LIKES.ID.isNotNull).`as`("IS_LIKED"),
                    DSL.field(USER_SAVES.ID.isNotNull).`as`("IS_SAVED")
                )
                .from(CONTENTS)
                .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
                .join(CONTENT_INTERACTIONS).on(CONTENT_INTERACTIONS.CONTENT_ID.eq(CONTENTS.ID))
                .join(USERS).on(USERS.ID.eq(CONTENTS.CREATOR_ID))
                .join(USER_PROFILES).on(USER_PROFILES.USER_ID.eq(USERS.ID))
                .leftJoin(USER_LIKES).on(
                    USER_LIKES.CONTENT_ID.eq(CONTENTS.ID)
                        .and(USER_LIKES.USER_ID.eq(userId.toString()))
                        .and(USER_LIKES.DELETED_AT.isNull)
                )
                .leftJoin(USER_SAVES).on(
                    USER_SAVES.CONTENT_ID.eq(CONTENTS.ID)
                        .and(USER_SAVES.USER_ID.eq(userId.toString()))
                        .and(USER_SAVES.DELETED_AT.isNull)
                )
                .where(CONTENTS.STATUS.eq("PUBLISHED"))
                .and(CONTENTS.DELETED_AT.isNull)
                .and(CONTENT_METADATA.DELETED_AT.isNull)
                .and(USERS.DELETED_AT.isNull)
                .and(USER_PROFILES.DELETED_AT.isNull)

            // 제외할 콘텐츠 필터링
            if (excludeContentIds.isNotEmpty()) {
                query = query.and(CONTENTS.ID.notIn(excludeContentIds.map { it.toString() }))
            }

            // 커서 기반 페이지네이션
            if (cursor != null) {
                query = query.and(CONTENTS.CREATED_AT.lt(
                    dslContext.select(CONTENTS.CREATED_AT)
                        .from(CONTENTS)
                        .where(CONTENTS.ID.eq(cursor.toString()))
                        .asField()
                ))
            }

            val records = query
                .orderBy(CONTENTS.CREATED_AT.desc())
                .limit(limit)
                .fetch()

            // 모든 콘텐츠 ID 추출
            val contentIds = records.map { UUID.fromString(it.get(CONTENTS.ID)) }

            // 자막 배치 조회 (N+1 문제 방지)
            val subtitlesMap = findSubtitlesByContentIds(contentIds)

            // 사진 배치 조회 (N+1 문제 방지)
            val photosMap = contentPhotoRepository.findByContentIds(contentIds)
                .mapValues { (_, photos) -> photos.map { it.photoUrl } }

            // 레코드를 FeedItemResponse로 변환
            records.map { record -> mapRecordToFeedItem(record, subtitlesMap, photosMap) }
        }
            .flatMapMany { Flux.fromIterable(it) }
    }

    /**
     * 팔로잉 피드 조회
     *
     * 사용자가 팔로우한 크리에이터의 최신 콘텐츠를 제공합니다.
     *
     * @param userId 사용자 ID
     * @param cursor 커서 (마지막 조회 콘텐츠 ID, null이면 첫 페이지)
     * @param limit 조회할 항목 수
     * @return 피드 아이템 목록
     */
    override fun findFollowingFeed(
        userId: UUID,
        cursor: UUID?,
        limit: Int
    ): Flux<FeedItemResponse> {
        return Mono.fromCallable {
            var query = dslContext
                .select(
                    // CONTENTS 필요 컬럼만 명시적으로 선택
                    CONTENTS.ID,
                    CONTENTS.CONTENT_TYPE,
                    CONTENTS.URL,
                    CONTENTS.THUMBNAIL_URL,
                    CONTENTS.DURATION,
                    CONTENTS.WIDTH,
                    CONTENTS.HEIGHT,
                    CONTENTS.CREATED_AT,
                    // CONTENT_METADATA 필요 컬럼만 명시적으로 선택
                    CONTENT_METADATA.TITLE,
                    CONTENT_METADATA.DESCRIPTION,
                    CONTENT_METADATA.CATEGORY,
                    CONTENT_METADATA.TAGS,
                    // CONTENT_INTERACTIONS 필요 컬럼만 명시적으로 선택
                    CONTENT_INTERACTIONS.LIKE_COUNT,
                    CONTENT_INTERACTIONS.COMMENT_COUNT,
                    CONTENT_INTERACTIONS.SAVE_COUNT,
                    CONTENT_INTERACTIONS.SHARE_COUNT,
                    CONTENT_INTERACTIONS.VIEW_COUNT,
                    // USERS, USER_PROFILES
                    USERS.ID,
                    USER_PROFILES.NICKNAME,
                    USER_PROFILES.PROFILE_IMAGE_URL,
                    USER_PROFILES.FOLLOWER_COUNT,
                    // USER_LIKES, USER_SAVES (사용자별 상태)
                    DSL.field(USER_LIKES.ID.isNotNull).`as`("IS_LIKED"),
                    DSL.field(USER_SAVES.ID.isNotNull).`as`("IS_SAVED")
                )
                .from(CONTENTS)
                .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
                .join(CONTENT_INTERACTIONS).on(CONTENT_INTERACTIONS.CONTENT_ID.eq(CONTENTS.ID))
                .join(USERS).on(USERS.ID.eq(CONTENTS.CREATOR_ID))
                .join(USER_PROFILES).on(USER_PROFILES.USER_ID.eq(USERS.ID))
                .join(FOLLOWS).on(FOLLOWS.FOLLOWING_ID.eq(USERS.ID))
                .leftJoin(USER_LIKES).on(
                    USER_LIKES.CONTENT_ID.eq(CONTENTS.ID)
                        .and(USER_LIKES.USER_ID.eq(userId.toString()))
                        .and(USER_LIKES.DELETED_AT.isNull)
                )
                .leftJoin(USER_SAVES).on(
                    USER_SAVES.CONTENT_ID.eq(CONTENTS.ID)
                        .and(USER_SAVES.USER_ID.eq(userId.toString()))
                        .and(USER_SAVES.DELETED_AT.isNull)
                )
                .where(FOLLOWS.FOLLOWER_ID.eq(userId.toString()))
                .and(FOLLOWS.DELETED_AT.isNull)
                .and(CONTENTS.STATUS.eq("PUBLISHED"))
                .and(CONTENTS.DELETED_AT.isNull)
                .and(CONTENT_METADATA.DELETED_AT.isNull)
                .and(USERS.DELETED_AT.isNull)
                .and(USER_PROFILES.DELETED_AT.isNull)

            // 커서 기반 페이지네이션
            if (cursor != null) {
                query = query.and(CONTENTS.CREATED_AT.lt(
                    dslContext.select(CONTENTS.CREATED_AT)
                        .from(CONTENTS)
                        .where(CONTENTS.ID.eq(cursor.toString()))
                        .asField()
                ))
            }

            val records = query
                .orderBy(CONTENTS.CREATED_AT.desc())
                .limit(limit)
                .fetch()

            // 모든 콘텐츠 ID 추출
            val contentIds = records.map { UUID.fromString(it.get(CONTENTS.ID)) }

            // 자막 배치 조회 (N+1 문제 방지)
            val subtitlesMap = findSubtitlesByContentIds(contentIds)

            // 사진 배치 조회 (N+1 문제 방지)
            val photosMap = contentPhotoRepository.findByContentIds(contentIds)
                .mapValues { (_, photos) -> photos.map { it.photoUrl } }

            // 레코드를 FeedItemResponse로 변환
            records.map { record -> mapRecordToFeedItem(record, subtitlesMap, photosMap) }
        }
            .flatMapMany { Flux.fromIterable(it) }
    }

    /**
     * 최근 본 콘텐츠 ID 목록 조회
     *
     * 중복 콘텐츠 방지를 위해 사용자가 최근 본 콘텐츠 ID 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @param limit 조회할 항목 수
     * @return 최근 본 콘텐츠 ID 목록
     */
    override fun findRecentlyViewedContentIds(userId: UUID, limit: Int): Flux<UUID> {
        return Mono.fromCallable {
            dslContext
                .select(USER_VIEW_HISTORY.CONTENT_ID)
                .from(USER_VIEW_HISTORY)
                .where(USER_VIEW_HISTORY.USER_ID.eq(userId.toString()))
                .and(USER_VIEW_HISTORY.DELETED_AT.isNull)
                .orderBy(USER_VIEW_HISTORY.WATCHED_AT.desc())
                .limit(limit)
                .fetch()
                .map { UUID.fromString(it.get(USER_VIEW_HISTORY.CONTENT_ID)) }
        }
            .flatMapMany { Flux.fromIterable(it) }
    }

    /**
     * 데이터베이스 레코드를 FeedItemResponse로 변환
     *
     * @param record JOOQ 레코드
     * @param subtitlesMap 콘텐츠 ID를 키로 하는 자막 정보 맵
     * @param photosMap 콘텐츠 ID를 키로 하는 사진 URL 목록 맵
     * @return FeedItemResponse
     */
    private fun mapRecordToFeedItem(
        record: org.jooq.Record,
        subtitlesMap: Map<UUID, List<SubtitleInfoResponse>>,
        photosMap: Map<UUID, List<String>>
    ): FeedItemResponse {
        val contentId = UUID.fromString(record.get(CONTENTS.ID))

        // 태그 파싱
        val tagsJson = record.get(CONTENT_METADATA.TAGS)
        val tags = if (tagsJson != null) {
            // JOOQ의 JSON.data()는 이미 파싱된 데이터를 반환
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(tagsJson.data(), List::class.java) as List<String>
        } else {
            emptyList()
        }

        // 자막은 미리 조회한 맵에서 가져오기
        val subtitles = subtitlesMap[contentId] ?: emptyList()

        // 사진 URL 목록은 미리 조회한 맵에서 가져오기
        val photoUrls = photosMap[contentId]

        return FeedItemResponse(
            contentId = contentId,
            contentType = ContentType.valueOf(record.get(CONTENTS.CONTENT_TYPE)!!),
            url = record.get(CONTENTS.URL)!!,
            photoUrls = photoUrls,
            thumbnailUrl = record.get(CONTENTS.THUMBNAIL_URL)!!,
            duration = record.get(CONTENTS.DURATION),
            width = record.get(CONTENTS.WIDTH)!!,
            height = record.get(CONTENTS.HEIGHT)!!,
            title = record.get(CONTENT_METADATA.TITLE)!!,
            description = record.get(CONTENT_METADATA.DESCRIPTION),
            category = Category.valueOf(record.get(CONTENT_METADATA.CATEGORY)!!),
            tags = tags,
            creator = CreatorInfoResponse(
                userId = UUID.fromString(record.get(USERS.ID)!!),
                nickname = record.get(USER_PROFILES.NICKNAME)!!,
                profileImageUrl = record.get(USER_PROFILES.PROFILE_IMAGE_URL),
                followerCount = record.get(USER_PROFILES.FOLLOWER_COUNT)!!
            ),
            interactions = InteractionInfoResponse(
                likeCount = record.get(CONTENT_INTERACTIONS.LIKE_COUNT)!!,
                commentCount = record.get(CONTENT_INTERACTIONS.COMMENT_COUNT)!!,
                saveCount = record.get(CONTENT_INTERACTIONS.SAVE_COUNT)!!,
                shareCount = record.get(CONTENT_INTERACTIONS.SHARE_COUNT)!!,
                viewCount = record.get(CONTENT_INTERACTIONS.VIEW_COUNT)!!,
                isLiked = record.get("IS_LIKED", Boolean::class.java) ?: false,
                isSaved = record.get("IS_SAVED", Boolean::class.java) ?: false
            ),
            subtitles = subtitles
        )
    }

    /**
     * 인기 콘텐츠 ID 목록 조회
     *
     * 인터랙션 가중치 기반 인기도 점수가 높은 콘텐츠를 조회합니다.
     *
     * ### 인기도 계산 공식
     * ```
     * popularity_score = view_count * 1.0
     *                  + like_count * 5.0
     *                  + comment_count * 3.0
     *                  + save_count * 7.0
     *                  + share_count * 10.0
     * ```
     *
     * @param limit 조회할 항목 수
     * @param excludeIds 제외할 콘텐츠 ID 목록
     * @return 인기 콘텐츠 ID 목록 (인기도 순 정렬)
     */
    override fun findPopularContentIds(limit: Int, excludeIds: List<UUID>): Flux<UUID> {
        return Mono.fromCallable {
            // 인기도 점수 계산식 (부동소수점 연산)
            val popularityScore = CONTENT_INTERACTIONS.VIEW_COUNT.cast(Double::class.java).mul(POPULARITY_WEIGHT_VIEW)
                .plus(CONTENT_INTERACTIONS.LIKE_COUNT.cast(Double::class.java).mul(POPULARITY_WEIGHT_LIKE))
                .plus(CONTENT_INTERACTIONS.COMMENT_COUNT.cast(Double::class.java).mul(POPULARITY_WEIGHT_COMMENT))
                .plus(CONTENT_INTERACTIONS.SAVE_COUNT.cast(Double::class.java).mul(POPULARITY_WEIGHT_SAVE))
                .plus(CONTENT_INTERACTIONS.SHARE_COUNT.cast(Double::class.java).mul(POPULARITY_WEIGHT_SHARE))

            var query = dslContext
                .select(CONTENTS.ID)
                .from(CONTENTS)
                .join(CONTENT_INTERACTIONS).on(CONTENT_INTERACTIONS.CONTENT_ID.eq(CONTENTS.ID))
                .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
                .where(CONTENTS.STATUS.eq("PUBLISHED"))
                .and(CONTENTS.DELETED_AT.isNull)
                .and(CONTENT_METADATA.DELETED_AT.isNull)

            // 제외할 콘텐츠 필터링
            if (excludeIds.isNotEmpty()) {
                query = query.and(CONTENTS.ID.notIn(excludeIds.map { it.toString() }))
            }

            query
                .orderBy(popularityScore.desc())
                .limit(limit)
                .fetch()
                .map { UUID.fromString(it.get(CONTENTS.ID)) }
        }
            .flatMapMany { Flux.fromIterable(it) }
    }

    /**
     * 신규 콘텐츠 ID 목록 조회
     *
     * 최근 업로드된 콘텐츠를 조회합니다.
     *
     * @param limit 조회할 항목 수
     * @param excludeIds 제외할 콘텐츠 ID 목록
     * @return 신규 콘텐츠 ID 목록 (최신순 정렬)
     */
    override fun findNewContentIds(limit: Int, excludeIds: List<UUID>): Flux<UUID> {
        return Mono.fromCallable {
            var query = dslContext
                .select(CONTENTS.ID)
                .from(CONTENTS)
                .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
                .where(CONTENTS.STATUS.eq("PUBLISHED"))
                .and(CONTENTS.DELETED_AT.isNull)
                .and(CONTENT_METADATA.DELETED_AT.isNull)

            // 제외할 콘텐츠 필터링
            if (excludeIds.isNotEmpty()) {
                query = query.and(CONTENTS.ID.notIn(excludeIds.map { it.toString() }))
            }

            query
                .orderBy(CONTENTS.CREATED_AT.desc())
                .limit(limit)
                .fetch()
                .map { UUID.fromString(it.get(CONTENTS.ID)) }
        }
            .flatMapMany { Flux.fromIterable(it) }
    }

    /**
     * 랜덤 콘텐츠 ID 목록 조회
     *
     * 무작위 콘텐츠를 조회하여 다양성을 확보합니다.
     *
     * @param limit 조회할 항목 수
     * @param excludeIds 제외할 콘텐츠 ID 목록
     * @return 랜덤 콘텐츠 ID 목록 (무작위 정렬)
     */
    override fun findRandomContentIds(limit: Int, excludeIds: List<UUID>): Flux<UUID> {
        return Mono.fromCallable {
            var query = dslContext
                .select(CONTENTS.ID)
                .from(CONTENTS)
                .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
                .where(CONTENTS.STATUS.eq("PUBLISHED"))
                .and(CONTENTS.DELETED_AT.isNull)
                .and(CONTENT_METADATA.DELETED_AT.isNull)

            // 제외할 콘텐츠 필터링
            if (excludeIds.isNotEmpty()) {
                query = query.and(CONTENTS.ID.notIn(excludeIds.map { it.toString() }))
            }

            query
                .orderBy(DSL.rand())
                .limit(limit)
                .fetch()
                .map { UUID.fromString(it.get(CONTENTS.ID)) }
        }
            .flatMapMany { Flux.fromIterable(it) }
    }

    /**
     * 콘텐츠 ID 목록 기반 상세 정보 조회
     *
     * 추천 알고리즘에서 받은 콘텐츠 ID 목록으로 상세 정보를 조회합니다.
     * ID 목록의 순서를 유지하여 반환합니다.
     *
     * @param contentIds 콘텐츠 ID 목록 (순서 유지)
     * @return 피드 아이템 목록 (입력 순서 유지)
     */
    override fun findByContentIds(userId: UUID, contentIds: List<UUID>): Flux<FeedItemResponse> {
        if (contentIds.isEmpty()) {
            return Flux.empty()
        }

        return Mono.fromCallable {
            val records = dslContext
                .select(
                    // CONTENTS 필요 컬럼만 명시적으로 선택
                    CONTENTS.ID,
                    CONTENTS.CONTENT_TYPE,
                    CONTENTS.URL,
                    CONTENTS.THUMBNAIL_URL,
                    CONTENTS.DURATION,
                    CONTENTS.WIDTH,
                    CONTENTS.HEIGHT,
                    CONTENTS.CREATED_AT,
                    // CONTENT_METADATA 필요 컬럼만 명시적으로 선택
                    CONTENT_METADATA.TITLE,
                    CONTENT_METADATA.DESCRIPTION,
                    CONTENT_METADATA.CATEGORY,
                    CONTENT_METADATA.TAGS,
                    // CONTENT_INTERACTIONS 필요 컬럼만 명시적으로 선택
                    CONTENT_INTERACTIONS.LIKE_COUNT,
                    CONTENT_INTERACTIONS.COMMENT_COUNT,
                    CONTENT_INTERACTIONS.SAVE_COUNT,
                    CONTENT_INTERACTIONS.SHARE_COUNT,
                    CONTENT_INTERACTIONS.VIEW_COUNT,
                    // USERS, USER_PROFILES
                    USERS.ID,
                    USER_PROFILES.NICKNAME,
                    USER_PROFILES.PROFILE_IMAGE_URL,
                    USER_PROFILES.FOLLOWER_COUNT,
                    // 사용자별 좋아요/저장 상태
                    DSL.field(USER_LIKES.ID.isNotNull).`as`("IS_LIKED"),
                    DSL.field(USER_SAVES.ID.isNotNull).`as`("IS_SAVED")
                )
                .from(CONTENTS)
                .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
                .join(CONTENT_INTERACTIONS).on(CONTENT_INTERACTIONS.CONTENT_ID.eq(CONTENTS.ID))
                .join(USERS).on(USERS.ID.eq(CONTENTS.CREATOR_ID))
                .join(USER_PROFILES).on(USER_PROFILES.USER_ID.eq(USERS.ID))
                .leftJoin(USER_LIKES).on(
                    USER_LIKES.CONTENT_ID.eq(CONTENTS.ID)
                        .and(USER_LIKES.USER_ID.eq(userId.toString()))
                        .and(USER_LIKES.DELETED_AT.isNull)
                )
                .leftJoin(USER_SAVES).on(
                    USER_SAVES.CONTENT_ID.eq(CONTENTS.ID)
                        .and(USER_SAVES.USER_ID.eq(userId.toString()))
                        .and(USER_SAVES.DELETED_AT.isNull)
                )
                .where(CONTENTS.ID.`in`(contentIds.map { it.toString() }))
                .and(CONTENTS.STATUS.eq("PUBLISHED"))
                .and(CONTENTS.DELETED_AT.isNull)
                .and(CONTENT_METADATA.DELETED_AT.isNull)
                .and(USERS.DELETED_AT.isNull)
                .and(USER_PROFILES.DELETED_AT.isNull)
                .fetch()

            // 자막 배치 조회 (N+1 문제 방지)
            val subtitlesMap = findSubtitlesByContentIds(contentIds)

            // 사진 배치 조회 (N+1 문제 방지)
            val photosMap = contentPhotoRepository.findByContentIds(contentIds)
                .mapValues { (_, photos) -> photos.map { it.photoUrl } }

            // 레코드를 FeedItemResponse로 변환
            val feedItemsMap = records
                .map { record -> mapRecordToFeedItem(record, subtitlesMap, photosMap) }
                .associateBy { it.contentId }

            // 입력된 ID 순서대로 정렬하여 반환
            contentIds.mapNotNull { feedItemsMap[it] }
        }
            .flatMapMany { Flux.fromIterable(it) }
    }

    /**
     * 여러 콘텐츠의 자막 정보를 배치로 조회 (N+1 문제 방지)
     *
     * @param contentIds 콘텐츠 ID 목록
     * @return 콘텐츠 ID를 키로 하는 자막 정보 맵
     */
    private fun findSubtitlesByContentIds(contentIds: List<UUID>): Map<UUID, List<SubtitleInfoResponse>> {
        if (contentIds.isEmpty()) {
            return emptyMap()
        }

        return dslContext
            .select(
                CONTENT_SUBTITLES.CONTENT_ID,
                CONTENT_SUBTITLES.LANGUAGE,
                CONTENT_SUBTITLES.SUBTITLE_URL
            )
            .from(CONTENT_SUBTITLES)
            .where(CONTENT_SUBTITLES.CONTENT_ID.`in`(contentIds.map { it.toString() }))
            .and(CONTENT_SUBTITLES.DELETED_AT.isNull)
            .fetch()
            .groupBy(
                { UUID.fromString(it.get(CONTENT_SUBTITLES.CONTENT_ID)) },
                { record ->
                    SubtitleInfoResponse(
                        language = record.get(CONTENT_SUBTITLES.LANGUAGE)!!,
                        subtitleUrl = record.get(CONTENT_SUBTITLES.SUBTITLE_URL)!!
                    )
                }
            )
    }

    /**
     * 콘텐츠 ID 목록의 카테고리 조회 (사용자 선호도 분석용)
     *
     * @param contentIds 콘텐츠 ID 목록
     * @return 카테고리 목록 (중복 포함)
     */
    override fun findCategoriesByContentIds(contentIds: List<UUID>): Flux<String> {
        if (contentIds.isEmpty()) {
            return Flux.empty()
        }

        return Mono.fromCallable {
            dslContext
                .select(CONTENT_METADATA.CATEGORY)
                .from(CONTENT_METADATA)
                .where(CONTENT_METADATA.CONTENT_ID.`in`(contentIds.map { it.toString() }))
                .and(CONTENT_METADATA.DELETED_AT.isNull)
                .fetch()
                .map { it.get(CONTENT_METADATA.CATEGORY)!! }
        }
            .flatMapMany { Flux.fromIterable(it) }
    }

    /**
     * 특정 카테고리의 인기 콘텐츠 ID 조회 (카테고리 기반 추천용)
     *
     * @param categories 카테고리 목록
     * @param limit 조회할 항목 수
     * @param excludeIds 제외할 콘텐츠 ID 목록
     * @return 인기 콘텐츠 ID 목록 (인기도 순 정렬)
     */
    override fun findPopularContentIdsByCategories(
        categories: List<String>,
        limit: Int,
        excludeIds: List<UUID>
    ): Flux<UUID> {
        if (categories.isEmpty()) {
            return Flux.empty()
        }

        return Mono.fromCallable {
            // 인기도 점수 계산
            val popularityScore = CONTENT_INTERACTIONS.VIEW_COUNT.cast(Double::class.java).mul(POPULARITY_WEIGHT_VIEW)
                .plus(CONTENT_INTERACTIONS.LIKE_COUNT.cast(Double::class.java).mul(POPULARITY_WEIGHT_LIKE))
                .plus(CONTENT_INTERACTIONS.COMMENT_COUNT.cast(Double::class.java).mul(POPULARITY_WEIGHT_COMMENT))
                .plus(CONTENT_INTERACTIONS.SAVE_COUNT.cast(Double::class.java).mul(POPULARITY_WEIGHT_SAVE))
                .plus(CONTENT_INTERACTIONS.SHARE_COUNT.cast(Double::class.java).mul(POPULARITY_WEIGHT_SHARE))

            val query = dslContext
                .select(CONTENTS.ID)
                .from(CONTENTS)
                .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
                .join(CONTENT_INTERACTIONS).on(CONTENT_INTERACTIONS.CONTENT_ID.eq(CONTENTS.ID))
                .where(CONTENT_METADATA.CATEGORY.`in`(categories))
                .and(CONTENTS.STATUS.eq("PUBLISHED"))
                .and(CONTENTS.DELETED_AT.isNull)
                .and(CONTENT_METADATA.DELETED_AT.isNull)
                .and(CONTENT_INTERACTIONS.DELETED_AT.isNull)

            // 제외할 ID 추가
            val finalQuery = if (excludeIds.isNotEmpty()) {
                query.and(CONTENTS.ID.notIn(excludeIds.map { it.toString() }))
            } else {
                query
            }

            finalQuery
                .orderBy(popularityScore.desc())
                .limit(limit)
                .fetch()
                .map { UUID.fromString(it.get(CONTENTS.ID)) }
        }
            .flatMapMany { Flux.fromIterable(it) }
    }

    companion object {
        /**
         * 인기도 점수 계산 가중치
         *
         * 각 인터랙션 유형별 가중치를 정의합니다.
         * 높은 가중치일수록 인기도 점수에 더 큰 영향을 미칩니다.
         */
        private const val POPULARITY_WEIGHT_VIEW = 1.0
        private const val POPULARITY_WEIGHT_LIKE = 5.0
        private const val POPULARITY_WEIGHT_COMMENT = 3.0
        private const val POPULARITY_WEIGHT_SAVE = 7.0
        private const val POPULARITY_WEIGHT_SHARE = 10.0
    }
}
