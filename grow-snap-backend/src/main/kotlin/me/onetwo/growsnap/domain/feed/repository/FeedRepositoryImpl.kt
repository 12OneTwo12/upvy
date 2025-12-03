package me.onetwo.growsnap.domain.feed.repository

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.feed.dto.CreatorInfoResponse
import me.onetwo.growsnap.domain.feed.dto.FeedItemResponse
import me.onetwo.growsnap.domain.feed.dto.InteractionInfoResponse
import me.onetwo.growsnap.domain.feed.dto.SubtitleInfoResponse
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENTS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_BLOCKS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_INTERACTIONS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_METADATA
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_PHOTOS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_SUBTITLES
import me.onetwo.growsnap.jooq.generated.tables.references.FOLLOWS
import me.onetwo.growsnap.jooq.generated.tables.references.USERS
import me.onetwo.growsnap.jooq.generated.tables.references.USER_BLOCKS
import me.onetwo.growsnap.jooq.generated.tables.references.USER_LIKES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_PROFILES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_SAVES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
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
) : FeedRepository {

    private val logger = LoggerFactory.getLogger(FeedRepositoryImpl::class.java)

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

        // 차단 필터링 조건 적용
        getBlockConditions(userId).forEach { query = query.and(it) }

        // 커서 기반 페이지네이션
        if (cursor != null) {
            query = query.and(CONTENTS.CREATED_AT.lt(
                dslContext.select(CONTENTS.CREATED_AT)
                    .from(CONTENTS)
                    .where(CONTENTS.ID.eq(cursor.toString()))
                    .asField()
            ))
        }

        // Execute query and collect contentIds first
        return Flux.from(query
                .orderBy(CONTENTS.CREATED_AT.desc())
                .limit(limit)
        )
            .collectList()
            .flatMapMany { records ->
                if (records.isEmpty()) {
                    return@flatMapMany Flux.empty<FeedItemResponse>()
                }

                // 모든 콘텐츠 ID 추출
                val contentIds = records.map { UUID.fromString(it.get(CONTENTS.ID)) }

                // 자막 배치 조회 (N+1 문제 방지)
                val subtitlesMono = findSubtitlesByContentIdsReactive(contentIds)

                // 사진 배치 조회 (N+1 문제 방지)
                val photosMono = findPhotosByContentIdsReactive(contentIds)

                // 두 맵을 병렬로 조회한 후 결합
                Mono.zip(subtitlesMono, photosMono)
                    .flatMapMany { tuple ->
                        val subtitlesMap = tuple.t1
                        val photosMap = tuple.t2
                        // 레코드를 FeedItemResponse로 변환
                        Flux.fromIterable(records.map { record -> mapRecordToFeedItem(record, subtitlesMap, photosMap) })
                    }
            }
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
        return Flux.from(
            dslContext
                .select(USER_VIEW_HISTORY.CONTENT_ID)
                .from(USER_VIEW_HISTORY)
                .where(USER_VIEW_HISTORY.USER_ID.eq(userId.toString()))
                .and(USER_VIEW_HISTORY.DELETED_AT.isNull)
                .orderBy(USER_VIEW_HISTORY.WATCHED_AT.desc())
                .limit(limit)
        ).map { record -> UUID.fromString(record.get(USER_VIEW_HISTORY.CONTENT_ID)) }
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
        record: Record,
        subtitlesMap: Map<UUID, List<SubtitleInfoResponse>>,
        photosMap: Map<UUID, List<String>>
    ): FeedItemResponse {
        val contentId = UUID.fromString(record.get(CONTENTS.ID))

        // 태그 파싱 - JOOQ가 JSON을 String으로 자동 변환
        val tagsString = record.get(CONTENT_METADATA.TAGS, String::class.java)
        val tags = if (tagsString != null && tagsString.isNotBlank()) {
            try {
                objectMapper.readValue(tagsString, object : TypeReference<List<String>>() {})
            } catch (e: JsonProcessingException) {
                // JSON 파싱 실패 시 빈 리스트 반환 (fallback)
                logger.warn("Failed to parse tags JSON for content $contentId: ${e.message}", e)
                emptyList()
            }
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
     * @param userId 사용자 ID (차단 필터링용)
     * @param limit 조회할 항목 수
     * @param excludeIds 제외할 콘텐츠 ID 목록
     * @param category 카테고리 필터 (null이면 전체 조회)
     * @return 인기 콘텐츠 ID 목록 (인기도 순 정렬)
     */
    override fun findPopularContentIds(userId: UUID, limit: Int, excludeIds: List<UUID>, category: Category?): Flux<UUID> {
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

        // 차단 필터링 조건 적용
        getBlockConditions(userId).forEach { query = query.and(it) }

        // 카테고리 필터링 (category가 null이 아닐 때만)
        if (category != null) {
            query = query.and(CONTENT_METADATA.CATEGORY.eq(category.name))
        }

        // 제외할 콘텐츠 필터링
        if (excludeIds.isNotEmpty()) {
            query = query.and(CONTENTS.ID.notIn(excludeIds.map { it.toString() }))
        }

        return Flux.from(
            query
                .orderBy(popularityScore.desc())
                .limit(limit)
        ).map { record -> UUID.fromString(record.get(CONTENTS.ID)) }
    }

    /**
     * 신규 콘텐츠 ID 목록 조회
     *
     * 최근 업로드된 콘텐츠를 조회합니다.
     *
     * @param userId 사용자 ID (차단 필터링용)
     * @param limit 조회할 항목 수
     * @param excludeIds 제외할 콘텐츠 ID 목록
     * @param category 카테고리 필터 (null이면 전체 조회)
     * @return 신규 콘텐츠 ID 목록 (최신순 정렬)
     */
    override fun findNewContentIds(userId: UUID, limit: Int, excludeIds: List<UUID>, category: Category?): Flux<UUID> {
        var query = dslContext
            .select(CONTENTS.ID)
            .from(CONTENTS)
            .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
            .where(CONTENTS.STATUS.eq("PUBLISHED"))
            .and(CONTENTS.DELETED_AT.isNull)
            .and(CONTENT_METADATA.DELETED_AT.isNull)

        // 차단 필터링 조건 적용
        getBlockConditions(userId).forEach { query = query.and(it) }

        // 카테고리 필터링 (category가 null이 아닐 때만)
        if (category != null) {
            query = query.and(CONTENT_METADATA.CATEGORY.eq(category.name))
        }

        // 제외할 콘텐츠 필터링
        if (excludeIds.isNotEmpty()) {
            query = query.and(CONTENTS.ID.notIn(excludeIds.map { it.toString() }))
        }

        return Flux.from(
            query
                .orderBy(CONTENTS.CREATED_AT.desc())
                .limit(limit)
        ).map { record -> UUID.fromString(record.get(CONTENTS.ID)) }
    }

    /**
     * 랜덤 콘텐츠 ID 목록 조회
     *
     * 무작위 콘텐츠를 조회하여 다양성을 확보합니다.
     *
     * @param userId 사용자 ID (차단 필터링용)
     * @param limit 조회할 항목 수
     * @param excludeIds 제외할 콘텐츠 ID 목록
     * @param category 카테고리 필터 (null이면 전체 조회)
     * @return 랜덤 콘텐츠 ID 목록 (무작위 정렬)
     */
    override fun findRandomContentIds(userId: UUID, limit: Int, excludeIds: List<UUID>, category: Category?): Flux<UUID> {
        var query = dslContext
            .select(CONTENTS.ID)
            .from(CONTENTS)
            .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
            .where(CONTENTS.STATUS.eq("PUBLISHED"))
            .and(CONTENTS.DELETED_AT.isNull)
            .and(CONTENT_METADATA.DELETED_AT.isNull)

        // 차단 필터링 조건 적용
        getBlockConditions(userId).forEach { query = query.and(it) }

        // 카테고리 필터링 (category가 null이 아닐 때만)
        if (category != null) {
            query = query.and(CONTENT_METADATA.CATEGORY.eq(category.name))
        }

        // 제외할 콘텐츠 필터링
        if (excludeIds.isNotEmpty()) {
            query = query.and(CONTENTS.ID.notIn(excludeIds.map { it.toString() }))
        }

        return Flux.from(
            query
                .orderBy(DSL.rand())
                .limit(limit)
        ).map { record -> UUID.fromString(record.get(CONTENTS.ID)) }
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

        // 차단 필터링 조건 적용
        getBlockConditions(userId).forEach { query = query.and(it) }

        return Flux.from(query)
            .collectList()
            .flatMapMany { records ->
                if (records.isEmpty()) {
                    return@flatMapMany Flux.empty<FeedItemResponse>()
                }

                // 자막 배치 조회 (N+1 문제 방지)
                val subtitlesMono = findSubtitlesByContentIdsReactive(contentIds)

                // 사진 배치 조회 (N+1 문제 방지)
                val photosMono = findPhotosByContentIdsReactive(contentIds)

                // 두 맵을 병렬로 조회한 후 결합
                Mono.zip(subtitlesMono, photosMono)
                    .flatMapMany { tuple ->
                        val subtitlesMap = tuple.t1
                        val photosMap = tuple.t2
                        // 레코드를 FeedItemResponse로 변환
                        val feedItemsMap = records
                            .map { record -> mapRecordToFeedItem(record, subtitlesMap, photosMap) }
                            .associateBy { it.contentId }

                        // 입력된 ID 순서대로 정렬하여 반환
                        Flux.fromIterable(contentIds.mapNotNull { feedItemsMap[it] })
                    }
            }
    }


    /**
     * 여러 콘텐츠의 자막 정보를 배치로 조회 (반응형 버전)
     */
    private fun findSubtitlesByContentIdsReactive(contentIds: List<UUID>): Mono<Map<UUID, List<SubtitleInfoResponse>>> {
        if (contentIds.isEmpty()) {
            return Mono.just(emptyMap())
        }

        return Flux.from(
            dslContext
                .select(
                    CONTENT_SUBTITLES.CONTENT_ID,
                    CONTENT_SUBTITLES.LANGUAGE,
                    CONTENT_SUBTITLES.SUBTITLE_URL
                )
                .from(CONTENT_SUBTITLES)
                .where(CONTENT_SUBTITLES.CONTENT_ID.`in`(contentIds.map { it.toString() }))
                .and(CONTENT_SUBTITLES.DELETED_AT.isNull)
        )
            .collectList()
            .map { records ->
                records.groupBy(
                    { UUID.fromString(it.get(CONTENT_SUBTITLES.CONTENT_ID)) },
                    { record ->
                        SubtitleInfoResponse(
                            language = record.get(CONTENT_SUBTITLES.LANGUAGE)!!,
                            subtitleUrl = record.get(CONTENT_SUBTITLES.SUBTITLE_URL)!!
                        )
                    }
                )
            }
    }

    /**
     * 여러 콘텐츠의 사진 URL을 배치로 조회 (반응형 버전)
     */
    private fun findPhotosByContentIdsReactive(contentIds: List<UUID>): Mono<Map<UUID, List<String>>> {
        if (contentIds.isEmpty()) {
            return Mono.just(emptyMap())
        }

        return Flux.from(
            dslContext
                .select(
                    CONTENT_PHOTOS.CONTENT_ID,
                    CONTENT_PHOTOS.PHOTO_URL
                )
                .from(CONTENT_PHOTOS)
                .where(CONTENT_PHOTOS.CONTENT_ID.`in`(contentIds.map { it.toString() }))
                .and(CONTENT_PHOTOS.DELETED_AT.isNull)
                .orderBy(CONTENT_PHOTOS.DISPLAY_ORDER.asc())
        )
            .collectList()
            .map { records ->
                records.groupBy(
                    { UUID.fromString(it.get(CONTENT_PHOTOS.CONTENT_ID)) },
                    { it.get(CONTENT_PHOTOS.PHOTO_URL)!! }
                )
            }
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

        return Flux.from(
            dslContext
                .select(CONTENT_METADATA.CATEGORY)
                .from(CONTENT_METADATA)
                .where(CONTENT_METADATA.CONTENT_ID.`in`(contentIds.map { it.toString() }))
                .and(CONTENT_METADATA.DELETED_AT.isNull)
        ).map { record -> record.get(CONTENT_METADATA.CATEGORY)!! }
    }


    /**
     * 팔로잉 콘텐츠 ID 목록 조회 (특정 카테고리)
     *
     * 사용자가 팔로우한 크리에이터의 최신 콘텐츠 중 특정 카테고리만 조회합니다.
     * 카테고리별 추천 알고리즘에서 사용됩니다.
     *
     * @param userId 사용자 ID
     * @param category 조회할 카테고리
     * @param limit 조회할 항목 수
     * @param excludeIds 제외할 콘텐츠 ID 목록
     * @return 팔로잉 콘텐츠 ID 목록 (최신순 정렬)
     */
    override fun findFollowingContentIdsByCategory(
        userId: UUID,
        category: Category,
        limit: Int,
        excludeIds: List<UUID>
    ): Flux<UUID> {
        var query = dslContext
            .select(CONTENTS.ID)
            .from(CONTENTS)
            .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
            .join(USERS).on(USERS.ID.eq(CONTENTS.CREATOR_ID))
            .join(FOLLOWS).on(FOLLOWS.FOLLOWING_ID.eq(USERS.ID))
            .where(FOLLOWS.FOLLOWER_ID.eq(userId.toString()))
            .and(FOLLOWS.DELETED_AT.isNull)
            .and(CONTENT_METADATA.CATEGORY.eq(category.name))
            .and(CONTENTS.STATUS.eq("PUBLISHED"))
            .and(CONTENTS.DELETED_AT.isNull)
            .and(CONTENT_METADATA.DELETED_AT.isNull)
            .and(USERS.DELETED_AT.isNull)

        // 차단 필터링 조건 적용
        getBlockConditions(userId).forEach { query = query.and(it) }

        // 제외할 콘텐츠 필터링
        if (excludeIds.isNotEmpty()) {
            query = query.and(CONTENTS.ID.notIn(excludeIds.map { it.toString() }))
        }

        return Flux.from(
            query
                .orderBy(CONTENTS.CREATED_AT.desc())
                .limit(limit)
        ).map { record -> UUID.fromString(record.get(CONTENTS.ID)) }
    }

    /**
     * 사용자의 차단 필터링 조건 생성
     *
     * 차단한 사용자의 콘텐츠와 차단한 콘텐츠를 필터링하는 JOOQ Condition을 생성합니다.
     *
     * @param userId 사용자 ID
     * @return 차단 필터링 조건 리스트 (2개)
     */
    private fun getBlockConditions(userId: UUID): List<Condition> {
        return listOf(
            // 차단한 사용자의 콘텐츠 제외
            CONTENTS.CREATOR_ID.notIn(
                dslContext.select(USER_BLOCKS.BLOCKED_ID)
                    .from(USER_BLOCKS)
                    .where(USER_BLOCKS.BLOCKER_ID.eq(userId.toString()))
                    .and(USER_BLOCKS.DELETED_AT.isNull)
            ),
            // 차단한 콘텐츠 제외
            CONTENTS.ID.notIn(
                dslContext.select(CONTENT_BLOCKS.CONTENT_ID)
                    .from(CONTENT_BLOCKS)
                    .where(CONTENT_BLOCKS.USER_ID.eq(userId.toString()))
                    .and(CONTENT_BLOCKS.DELETED_AT.isNull)
            )
        )
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
