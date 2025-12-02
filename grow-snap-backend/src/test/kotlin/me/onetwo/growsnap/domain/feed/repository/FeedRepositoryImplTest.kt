package me.onetwo.growsnap.domain.feed.repository

import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.user.model.Follow
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.FollowRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENTS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_INTERACTIONS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_METADATA
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_PHOTOS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_SUBTITLES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY
import org.jooq.DSLContext
import org.jooq.JSON
import reactor.core.publisher.Mono
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * FeedRepository 통합 테스트
 *
 * 실제 데이터베이스(H2)를 사용하여 피드 조회 기능을 검증합니다.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@DisplayName("피드 Repository 통합 테스트")
class FeedRepositoryImplTest {

    @Autowired
    private lateinit var feedRepository: FeedRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var followRepository: FollowRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    private lateinit var viewer: User
    private lateinit var creator1: User
    private lateinit var creator2: User
    private lateinit var content1Id: UUID
    private lateinit var content2Id: UUID
    private lateinit var content3Id: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트 데이터 준비

        // 사용자 생성
        viewer = userRepository.save(
            User(
                email = "viewer@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "viewer-123",
                role = UserRole.USER
            )
        ).block()!!

        creator1 = userRepository.save(
            User(
                email = "creator1@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "creator1-123",
                role = UserRole.USER
            )
        ).block()!!

        creator2 = userRepository.save(
            User(
                email = "creator2@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "creator2-123",
                role = UserRole.USER
            )
        ).block()!!

        // 프로필 생성
        userProfileRepository.save(
            UserProfile(
                userId = viewer.id!!,
                nickname = "Viewer",
                createdBy = viewer.id.toString()!!
            )
        ).block()

        userProfileRepository.save(
            UserProfile(
                userId = creator1.id!!,
                nickname = "Creator1",
                createdBy = creator1.id.toString()!!
            )
        ).block()

        userProfileRepository.save(
            UserProfile(
                userId = creator2.id!!,
                nickname = "Creator2",
                createdBy = creator2.id.toString()!!
            )
        ).block()

        // 콘텐츠 3개 생성
        content1Id = UUID.randomUUID()
        content2Id = UUID.randomUUID()
        content3Id = UUID.randomUUID()

        // Content 1 (Creator1, 가장 최신)
        insertContent(
            contentId = content1Id,
            creatorId = creator1.id!!,
            title = "Test Video 1",
            createdAt = Instant.now().minus(1, ChronoUnit.HOURS)
        )

        // Content 2 (Creator2, 중간)
        insertContent(
            contentId = content2Id,
            creatorId = creator2.id!!,
            title = "Test Video 2",
            createdAt = Instant.now().minus(2, ChronoUnit.HOURS)
        )

        // Content 3 (Creator1, 가장 오래됨)
        insertContent(
            contentId = content3Id,
            creatorId = creator1.id!!,
            title = "Test Video 3",
            createdAt = Instant.now().minus(3, ChronoUnit.HOURS)
        )
    }

    @AfterEach
    fun tearDown() {
        // Delete in correct order to avoid FK constraint violations
        Mono.from(dslContext.deleteFrom(me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_SUBTITLES)).block()
        Mono.from(dslContext.deleteFrom(me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_PHOTOS)).block()
        Mono.from(dslContext.deleteFrom(USER_VIEW_HISTORY)).block()
        Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
        Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
        Mono.from(dslContext.deleteFrom(CONTENTS)).block()
        Mono.from(dslContext.deleteFrom(me.onetwo.growsnap.jooq.generated.tables.references.FOLLOWS)).block()
        Mono.from(dslContext.deleteFrom(me.onetwo.growsnap.jooq.generated.tables.references.USER_PROFILES)).block()
        Mono.from(dslContext.deleteFrom(me.onetwo.growsnap.jooq.generated.tables.references.USERS)).block()
    }

    @Nested
    @DisplayName("findFollowingFeed - 팔로잉 피드 조회")
    inner class FindFollowingFeed {

        @Test
        @DisplayName("팔로우한 크리에이터의 콘텐츠만 반환한다")
        fun findFollowingFeed_ReturnsOnlyFollowedCreatorsContent() {
            // Given: viewer가 creator1을 팔로우
            followRepository.save(
                Follow(
                    followerId = viewer.id!!,
                    followingId = creator1.id!!,
                    createdBy = viewer.id.toString()!!
                )
            ).block()

            // When
            val result = feedRepository.findFollowingFeed(
                userId = viewer.id!!,
                cursor = null,
                limit = 10
            ).collectList().block()!!

            // Then: creator1의 콘텐츠만 반환 (content1, content3)
            assertEquals(2, result.size)
            assertEquals(content1Id, result[0].contentId)
            assertEquals(content3Id, result[1].contentId)
        }

        @Test
        @DisplayName("팔로우하지 않은 경우, 빈 목록을 반환한다")
        fun findFollowingFeed_WithNoFollowing_ReturnsEmptyList() {
            // When: 아무도 팔로우하지 않음
            val result = feedRepository.findFollowingFeed(
                userId = viewer.id!!,
                cursor = null,
                limit = 10
            ).collectList().block()!!

            // Then: 빈 목록
            assertEquals(0, result.size)
        }

        @Test
        @DisplayName("여러 크리에이터를 팔로우하면, 모든 크리에이터의 콘텐츠를 반환한다")
        fun findFollowingFeed_WithMultipleFollowing_ReturnsAllContent() {
            // Given: viewer가 creator1과 creator2를 팔로우
            followRepository.save(
                Follow(
                    followerId = viewer.id!!,
                    followingId = creator1.id!!,
                    createdBy = viewer.id.toString()!!
                )
            ).block()
            followRepository.save(
                Follow(
                    followerId = viewer.id!!,
                    followingId = creator2.id!!,
                    createdBy = viewer.id.toString()!!
                )
            ).block()

            // When
            val result = feedRepository.findFollowingFeed(
                userId = viewer.id!!,
                cursor = null,
                limit = 10
            ).collectList().block()!!

            // Then: 모든 콘텐츠 반환 (content1, content2, content3)
            assertEquals(3, result.size)
        }

        @Test
        @DisplayName("PHOTO 타입 콘텐츠 조회 시, photoUrls가 순서대로 반환된다")
        fun findFollowingFeed_WithPhotoContent_ReturnsPhotoUrlsInOrder() {
            // Given: viewer가 creator1을 팔로우
            followRepository.save(
                Follow(
                    followerId = viewer.id!!,
                    followingId = creator1.id!!,
                    createdBy = viewer.id.toString()!!
                )
            ).block()

            // Given: PHOTO 타입 콘텐츠 생성
            val photoContentId = UUID.randomUUID()
            insertPhotoContent(
                contentId = photoContentId,
                creatorId = creator1.id!!,
                title = "Test Photo Content",
                createdAt = Instant.now()
            )
            // display_order 역순으로 삽입 (정렬 테스트)
            insertContentPhoto(
                contentId = photoContentId,
                photoUrl = "https://example.com/photo2.jpg",
                displayOrder = 2,
                createdBy = creator1.id!!
            )
            insertContentPhoto(
                contentId = photoContentId,
                photoUrl = "https://example.com/photo1.jpg",
                displayOrder = 1,
                createdBy = creator1.id!!
            )
            insertContentPhoto(
                contentId = photoContentId,
                photoUrl = "https://example.com/photo0.jpg",
                displayOrder = 0,
                createdBy = creator1.id!!
            )

            // When
            val result = feedRepository.findFollowingFeed(
                userId = viewer.id!!,
                cursor = null,
                limit = 10
            ).collectList().block()!!

            // Then: PHOTO 콘텐츠의 photoUrls가 display_order 순으로 반환됨 (역순 삽입했지만 정렬됨)
            val photoContent = result.find { it.contentId == photoContentId }!!
            assertNotNull(photoContent.photoUrls)
            assertEquals(3, photoContent.photoUrls!!.size)
            assertEquals("https://example.com/photo0.jpg", photoContent.photoUrls!![0])
            assertEquals("https://example.com/photo1.jpg", photoContent.photoUrls!![1])
            assertEquals("https://example.com/photo2.jpg", photoContent.photoUrls!![2])
        }
    }

    @Nested
    @DisplayName("findRecentlyViewedContentIds - 최근 본 콘텐츠 ID 조회")
    inner class FindRecentlyViewedContentIds {

        @Test
        @DisplayName("최근 본 콘텐츠 ID 목록을 시간 역순으로 반환한다")
        fun findRecentlyViewedContentIds_ReturnsRecentlyViewedInDescOrder() {
            // Given: 시청 기록 추가 (content3 → content1 → content2 순서로 시청)
            insertViewHistory(
                userId = viewer.id!!,
                contentId = content3Id,
                watchedAt = Instant.now().minus(3, ChronoUnit.HOURS)
            )
            insertViewHistory(
                userId = viewer.id!!,
                contentId = content1Id,
                watchedAt = Instant.now().minus(1, ChronoUnit.HOURS)
            )
            insertViewHistory(
                userId = viewer.id!!,
                contentId = content2Id,
                watchedAt = Instant.now()
            )

            // When
            val result = feedRepository.findRecentlyViewedContentIds(
                userId = viewer.id!!,
                limit = 10
            ).collectList().block()!!

            // Then: 최근 시청 순서대로 반환 (content2 → content1 → content3)
            assertEquals(3, result.size)
            assertEquals(content2Id, result[0])
            assertEquals(content1Id, result[1])
            assertEquals(content3Id, result[2])
        }

        @Test
        @DisplayName("limit 이하의 결과만 반환한다")
        fun findRecentlyViewedContentIds_WithLimit_ReturnsLimitedResults() {
            // Given: 시청 기록 3개
            insertViewHistory(viewer.id!!, content1Id, Instant.now().minus(3, ChronoUnit.HOURS))
            insertViewHistory(viewer.id!!, content2Id, Instant.now().minus(2, ChronoUnit.HOURS))
            insertViewHistory(viewer.id!!, content3Id, Instant.now().minus(1, ChronoUnit.HOURS))

            // When: limit=2
            val result = feedRepository.findRecentlyViewedContentIds(
                userId = viewer.id!!,
                limit = 2
            ).collectList().block()!!

            // Then: 최근 2개만 반환
            assertEquals(2, result.size)
        }

        @Test
        @DisplayName("시청 기록이 없으면, 빈 목록을 반환한다")
        fun findRecentlyViewedContentIds_WithNoHistory_ReturnsEmptyList() {
            // When: 시청 기록 없음
            val result = feedRepository.findRecentlyViewedContentIds(
                userId = viewer.id!!,
                limit = 10
            ).collectList().block()!!

            // Then: 빈 목록
            assertEquals(0, result.size)
        }
    }

    @Nested
    @DisplayName("findPopularContentIds - 인기 콘텐츠 ID 조회")
    inner class FindPopularContentIds {

        @Test
        @DisplayName("인기도 순으로 콘텐츠 ID를 반환한다")
        fun findPopularContentIds_ReturnsOrderedByPopularity() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 인기도가 다른 3개의 콘텐츠
            val highPopularityId = UUID.randomUUID()
            val mediumPopularityId = UUID.randomUUID()
            val lowPopularityId = UUID.randomUUID()

            // 고인기: view*1 + like*5 + comment*3 + save*7 + share*10 = 10000 + 5000 + 3000 + 7000 + 10000 = 35000
            insertContentWithInteractions(highPopularityId, creator1.id!!, "High Popularity", 10000, 1000, 1000, 1000, 1000)
            // 중인기: 5000 + 2500 + 1500 + 3500 + 5000 = 17500
            insertContentWithInteractions(mediumPopularityId, creator1.id!!, "Medium Popularity", 5000, 500, 500, 500, 500)
            // 저인기: 1000 + 500 + 300 + 700 + 1000 = 3500
            insertContentWithInteractions(lowPopularityId, creator2.id!!, "Low Popularity", 1000, 100, 100, 100, 100)

            // When: 인기 콘텐츠 조회
            val result = feedRepository.findPopularContentIds(viewer.id!!, 10, emptyList())
                .collectList()
                .block()!!

            // Then: 인기도 순으로 정렬되어 반환
            assertEquals(3, result.size)
            assertEquals(highPopularityId, result[0])
            assertEquals(mediumPopularityId, result[1])
            assertEquals(lowPopularityId, result[2])
        }

        @Test
        @DisplayName("제외할 콘텐츠 ID가 주어지면, 해당 콘텐츠를 제외하고 반환한다")
        fun findPopularContentIds_WithExcludeIds_ExcludesSpecifiedContent() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 3개의 콘텐츠
            val id1 = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            val id3 = UUID.randomUUID()

            insertContentWithInteractions(id1, creator1.id!!, "Content 1", 3000, 300, 300, 300, 300)
            insertContentWithInteractions(id2, creator1.id!!, "Content 2", 2000, 200, 200, 200, 200)
            insertContentWithInteractions(id3, creator2.id!!, "Content 3", 1000, 100, 100, 100, 100)

            // When: id2를 제외하고 조회
            val result = feedRepository.findPopularContentIds(viewer.id!!, 10, listOf(id2))
                .collectList()
                .block()!!

            // Then: id1, id3만 반환 (id2 제외)
            assertEquals(2, result.size)
            assertTrue(result.contains(id1))
            assertFalse(result.contains(id2))
            assertTrue(result.contains(id3))
        }

        @Test
        @DisplayName("limit보다 적은 콘텐츠가 있으면, 있는 만큼만 반환한다")
        fun findPopularContentIds_WithLimitGreaterThanAvailable_ReturnsAllAvailable() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 2개의 콘텐츠
            val id1 = UUID.randomUUID()
            val id2 = UUID.randomUUID()

            insertContentWithInteractions(id1, creator1.id!!, "Content 1", 2000, 200, 200, 200, 200)
            insertContentWithInteractions(id2, creator1.id!!, "Content 2", 1000, 100, 100, 100, 100)

            // When: limit=10으로 조회 (실제 콘텐츠는 2개)
            val result = feedRepository.findPopularContentIds(viewer.id!!, 10, emptyList())
                .collectList()
                .block()!!

            // Then: 2개만 반환
            assertEquals(2, result.size)
        }

        @Test
        @DisplayName("삭제된 콘텐츠는 조회되지 않는다")
        fun findPopularContentIds_ExcludesDeletedContent() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 정상 콘텐츠 1개, 삭제된 콘텐츠 1개
            val activeId = UUID.randomUUID()
            val deletedId = UUID.randomUUID()

            insertContentWithInteractions(activeId, creator1.id!!, "Active Content", 2000, 200, 200, 200, 200)
            insertContentWithInteractions(deletedId, creator1.id!!, "Deleted Content", 3000, 300, 300, 300, 300)

            // 콘텐츠 삭제 (Soft Delete)
            Mono.from(dslContext.update(CONTENTS)
                .set(CONTENTS.DELETED_AT, Instant.now())
                .where(CONTENTS.ID.eq(deletedId.toString()))).block()

            // When: 인기 콘텐츠 조회
            val result = feedRepository.findPopularContentIds(viewer.id!!, 10, emptyList())
                .collectList()
                .block()!!

            // Then: 삭제되지 않은 콘텐츠만 반환
            assertEquals(1, result.size)
            assertEquals(activeId, result[0])
        }
    }

    @Nested
    @DisplayName("findNewContentIds - 신규 콘텐츠 ID 조회")
    inner class FindNewContentIds {

        @Test
        @DisplayName("최신순으로 콘텐츠 ID를 반환한다")
        fun findNewContentIds_ReturnsOrderedByCreatedAtDesc() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 생성 시각이 다른 3개의 콘텐츠
            val now = Instant.now()
            val newestId = UUID.randomUUID()
            val middleId = UUID.randomUUID()
            val oldestId = UUID.randomUUID()

            insertContent(oldestId, creator1.id!!, "Oldest Content", now.minus(3, ChronoUnit.DAYS))
            insertContent(middleId, creator1.id!!, "Middle Content", now.minus(2, ChronoUnit.DAYS))
            insertContent(newestId, creator2.id!!, "Newest Content", now.minus(1, ChronoUnit.DAYS))

            // When: 신규 콘텐츠 조회
            val result = feedRepository.findNewContentIds(viewer.id!!, 10, emptyList())
                .collectList()
                .block()!!

            // Then: 최신순으로 정렬되어 반환
            assertEquals(3, result.size)
            assertEquals(newestId, result[0])
            assertEquals(middleId, result[1])
            assertEquals(oldestId, result[2])
        }

        @Test
        @DisplayName("제외할 콘텐츠 ID가 주어지면, 해당 콘텐츠를 제외하고 반환한다")
        fun findNewContentIds_WithExcludeIds_ExcludesSpecifiedContent() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 3개의 콘텐츠
            val now = Instant.now()
            val id1 = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            val id3 = UUID.randomUUID()

            insertContent(id1, creator1.id!!, "Content 1", now.minus(1, ChronoUnit.DAYS))
            insertContent(id2, creator1.id!!, "Content 2", now.minus(2, ChronoUnit.DAYS))
            insertContent(id3, creator2.id!!, "Content 3", now.minus(3, ChronoUnit.DAYS))

            // When: id2를 제외하고 조회
            val result = feedRepository.findNewContentIds(viewer.id!!, 10, listOf(id2))
                .collectList()
                .block()!!

            // Then: id1, id3만 반환 (id2 제외)
            assertEquals(2, result.size)
            assertTrue(result.contains(id1))
            assertFalse(result.contains(id2))
            assertTrue(result.contains(id3))
        }

        @Test
        @DisplayName("limit만큼만 콘텐츠를 반환한다")
        fun findNewContentIds_ReturnsUpToLimit() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 5개의 콘텐츠
            val now = Instant.now()
            repeat(5) { index ->
                insertContent(UUID.randomUUID(), creator1.id!!, "Content $index", now.minus(index.toLong(), ChronoUnit.DAYS))
            }

            // When: limit=3으로 조회
            val result = feedRepository.findNewContentIds(viewer.id!!, 3, emptyList())
                .collectList()
                .block()!!

            // Then: 3개만 반환
            assertEquals(3, result.size)
        }

        @Test
        @DisplayName("삭제된 콘텐츠는 조회되지 않는다")
        fun findNewContentIds_ExcludesDeletedContent() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 정상 콘텐츠 1개, 삭제된 콘텐츠 1개
            val now = Instant.now()
            val activeId = UUID.randomUUID()
            val deletedId = UUID.randomUUID()

            insertContent(activeId, creator1.id!!, "Active Content", now.minus(1, ChronoUnit.DAYS))
            insertContent(deletedId, creator1.id!!, "Deleted Content", now)

            // 콘텐츠 삭제 (Soft Delete)
            Mono.from(dslContext.update(CONTENTS)
                .set(CONTENTS.DELETED_AT, Instant.now())
                .where(CONTENTS.ID.eq(deletedId.toString()))).block()

            // When: 신규 콘텐츠 조회
            val result = feedRepository.findNewContentIds(viewer.id!!, 10, emptyList())
                .collectList()
                .block()!!

            // Then: 삭제되지 않은 콘텐츠만 반환
            assertEquals(1, result.size)
            assertEquals(activeId, result[0])
        }
    }

    @Nested
    @DisplayName("findRandomContentIds - 랜덤 콘텐츠 ID 조회")
    inner class FindRandomContentIds {

        @Test
        @DisplayName("랜덤 순서로 콘텐츠 ID를 반환한다")
        fun findRandomContentIds_ReturnsRandomOrder() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 여러 개의 콘텐츠
            val contentIds = (1..10).map { UUID.randomUUID() }
            contentIds.forEach { id ->
                insertContent(id, creator1.id!!, "Content $id", Instant.now())
            }

            // When: 랜덤 콘텐츠 조회 (여러 번 실행하여 순서가 다른지 확인)
            val result1 = feedRepository.findRandomContentIds(viewer.id!!, 10, emptyList())
                .collectList()
                .block()!!

            val result2 = feedRepository.findRandomContentIds(viewer.id!!, 10, emptyList())
                .collectList()
                .block()!!

            // Then: 같은 콘텐츠들이 반환되지만, 순서는 다를 수 있음 (랜덤이므로 항상 다르지는 않을 수 있음)
            assertEquals(10, result1.size)
            assertEquals(10, result2.size)
            // 모든 ID가 포함되어 있는지 확인
            assertTrue(contentIds.all { result1.contains(it) })
            assertTrue(contentIds.all { result2.contains(it) })
        }

        @Test
        @DisplayName("제외할 콘텐츠 ID가 주어지면, 해당 콘텐츠를 제외하고 반환한다")
        fun findRandomContentIds_WithExcludeIds_ExcludesSpecifiedContent() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 5개의 콘텐츠
            val ids = (1..5).map { UUID.randomUUID() }
            ids.forEach { id ->
                insertContent(id, creator1.id!!, "Content $id", Instant.now())
            }

            // When: ids[2]를 제외하고 조회
            val result = feedRepository.findRandomContentIds(viewer.id!!, 10, listOf(ids[2]))
                .collectList()
                .block()!!

            // Then: 4개만 반환 (ids[2] 제외)
            assertEquals(4, result.size)
            assertFalse(result.contains(ids[2]))
        }

        @Test
        @DisplayName("limit만큼만 콘텐츠를 반환한다")
        fun findRandomContentIds_ReturnsUpToLimit() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 10개의 콘텐츠
            repeat(10) { index ->
                insertContent(UUID.randomUUID(), creator1.id!!, "Content $index", Instant.now())
            }

            // When: limit=5로 조회
            val result = feedRepository.findRandomContentIds(viewer.id!!, 5, emptyList())
                .collectList()
                .block()!!

            // Then: 5개만 반환
            assertEquals(5, result.size)
        }

        @Test
        @DisplayName("삭제된 콘텐츠는 조회되지 않는다")
        fun findRandomContentIds_ExcludesDeletedContent() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 정상 콘텐츠 3개, 삭제된 콘텐츠 1개
            val activeIds = (1..3).map { UUID.randomUUID() }
            val deletedId = UUID.randomUUID()

            activeIds.forEach { id ->
                insertContent(id, creator1.id!!, "Active Content $id", Instant.now())
            }
            insertContent(deletedId, creator1.id!!, "Deleted Content", Instant.now())

            // 콘텐츠 삭제 (Soft Delete)
            Mono.from(dslContext.update(CONTENTS)
                .set(CONTENTS.DELETED_AT, Instant.now())
                .where(CONTENTS.ID.eq(deletedId.toString()))).block()

            // When: 랜덤 콘텐츠 조회
            val result = feedRepository.findRandomContentIds(viewer.id!!, 10, emptyList())
                .collectList()
                .block()!!

            // Then: 삭제되지 않은 콘텐츠만 반환
            assertEquals(3, result.size)
            assertFalse(result.contains(deletedId))
        }
    }

    // TODO: Commented out - findContentIdsByCategory method was deleted during CategoryFeedSortType refactoring
    /*
    @Nested
    @DisplayName("findContentIdsByCategory - 카테고리별 콘텐츠 ID 조회")
    inner class FindContentIdsByCategory {

        @Test
        @DisplayName("POPULAR 정렬 시, 인기도 순으로 콘텐츠 ID를 반환한다")
        fun findContentIdsByCategory_WithPopularSort_ReturnsOrderedByPopularity() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: PROGRAMMING 카테고리의 콘텐츠 3개 (인기도 다름)
            val highPopularityId = UUID.randomUUID()
            val mediumPopularityId = UUID.randomUUID()
            val lowPopularityId = UUID.randomUUID()

            // 고인기: 35000
            insertContentWithCategory(highPopularityId, creator1.id!!, "High Popularity", Category.PROGRAMMING,
                InteractionCounts(10000, 1000, 1000, 1000, 1000))
            // 중인기: 17500
            insertContentWithCategory(mediumPopularityId, creator1.id!!, "Medium Popularity", Category.PROGRAMMING,
                InteractionCounts(5000, 500, 500, 500, 500))
            // 저인기: 3500
            insertContentWithCategory(lowPopularityId, creator2.id!!, "Low Popularity", Category.PROGRAMMING,
                InteractionCounts(1000, 100, 100, 100, 100))

            // When: PROGRAMMING 카테고리, POPULAR 정렬로 조회
            val result = feedRepository.findContentIdsByCategory(
                category = Category.PROGRAMMING,
                sortBy = me.onetwo.growsnap.domain.feed.model.CategoryFeedSortType.POPULAR,
                cursor = null,
                limit = 10
            ).collectList().block()!!

            // Then: 인기도 순으로 정렬
            assertEquals(3, result.size)
            assertEquals(highPopularityId, result[0])
            assertEquals(mediumPopularityId, result[1])
            assertEquals(lowPopularityId, result[2])
        }

        @Test
        @DisplayName("RECENT 정렬 시, 최신순으로 콘텐츠 ID를 반환한다")
        fun findContentIdsByCategory_WithRecentSort_ReturnsOrderedByCreatedAt() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: PROGRAMMING 카테고리의 콘텐츠 3개 (생성 시각 다름)
            val now = Instant.now()
            val newestId = UUID.randomUUID()
            val middleId = UUID.randomUUID()
            val oldestId = UUID.randomUUID()

            insertContentWithCategory(oldestId, creator1.id!!, "Oldest", Category.PROGRAMMING,
                InteractionCounts(), now.minus(3, ChronoUnit.DAYS))
            insertContentWithCategory(middleId, creator1.id!!, "Middle", Category.PROGRAMMING,
                InteractionCounts(), now.minus(2, ChronoUnit.DAYS))
            insertContentWithCategory(newestId, creator2.id!!, "Newest", Category.PROGRAMMING,
                InteractionCounts(), now.minus(1, ChronoUnit.DAYS))

            // When: PROGRAMMING 카테고리, RECENT 정렬로 조회
            val result = feedRepository.findContentIdsByCategory(
                category = Category.PROGRAMMING,
                sortBy = me.onetwo.growsnap.domain.feed.model.CategoryFeedSortType.RECENT,
                cursor = null,
                limit = 10
            ).collectList().block()!!

            // Then: 최신순으로 정렬
            assertEquals(3, result.size)
            assertEquals(newestId, result[0])
            assertEquals(middleId, result[1])
            assertEquals(oldestId, result[2])
        }

        @Test
        @DisplayName("특정 카테고리의 콘텐츠만 반환하고, 다른 카테고리는 제외한다")
        fun findContentIdsByCategory_ReturnsOnlySpecifiedCategory() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 여러 카테고리의 콘텐츠
            val programmingId1 = UUID.randomUUID()
            val programmingId2 = UUID.randomUUID()
            val artId = UUID.randomUUID()
            val fitnessId = UUID.randomUUID()

            insertContentWithCategory(programmingId1, creator1.id!!, "Programming 1", Category.PROGRAMMING,
                InteractionCounts(100, 10, 10, 10, 10))
            insertContentWithCategory(programmingId2, creator1.id!!, "Programming 2", Category.PROGRAMMING,
                InteractionCounts(200, 20, 20, 20, 20))
            insertContentWithCategory(artId, creator2.id!!, "Art", Category.ART,
                InteractionCounts(300, 30, 30, 30, 30))
            insertContentWithCategory(fitnessId, creator2.id!!, "Health", Category.HEALTH,
                InteractionCounts(400, 40, 40, 40, 40))

            // When: PROGRAMMING 카테고리만 조회
            val result = feedRepository.findContentIdsByCategory(
                category = Category.PROGRAMMING,
                sortBy = me.onetwo.growsnap.domain.feed.model.CategoryFeedSortType.POPULAR,
                cursor = null,
                limit = 10
            ).collectList().block()!!

            // Then: PROGRAMMING 카테고리의 콘텐츠만 반환
            assertEquals(2, result.size)
            assertTrue(result.contains(programmingId1))
            assertTrue(result.contains(programmingId2))
            assertFalse(result.contains(artId))
            assertFalse(result.contains(fitnessId))
        }

        @Test
        @DisplayName("커서(offset)를 사용하여 페이지네이션이 작동한다")
        fun findContentIdsByCategory_WithCursor_ReturnsPaginatedResults() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: PROGRAMMING 카테고리의 콘텐츠 5개
            val ids = (1..5).map { UUID.randomUUID() }
            ids.forEachIndexed { index, id ->
                insertContentWithCategory(id, creator1.id!!, "Content $index", Category.PROGRAMMING,
                    InteractionCounts(1000 - (index * 100), 100, 100, 100, 100))
            }

            // When: cursor=null, limit=2 (첫 페이지)
            val page1 = feedRepository.findContentIdsByCategory(
                category = Category.PROGRAMMING,
                sortBy = me.onetwo.growsnap.domain.feed.model.CategoryFeedSortType.POPULAR,
                cursor = null,
                limit = 2
            ).collectList().block()!!

            // Then: 첫 2개 반환
            assertEquals(2, page1.size)
            assertEquals(ids[0], page1[0])
            assertEquals(ids[1], page1[1])

            // When: cursor=2, limit=2 (두 번째 페이지)
            val page2 = feedRepository.findContentIdsByCategory(
                category = Category.PROGRAMMING,
                sortBy = me.onetwo.growsnap.domain.feed.model.CategoryFeedSortType.POPULAR,
                cursor = "2",
                limit = 2
            ).collectList().block()!!

            // Then: 다음 2개 반환 (offset=2부터)
            assertEquals(2, page2.size)
            assertEquals(ids[2], page2[0])
            assertEquals(ids[3], page2[1])
        }

        @Test
        @DisplayName("limit만큼만 콘텐츠를 반환한다")
        fun findContentIdsByCategory_ReturnsUpToLimit() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: PROGRAMMING 카테고리의 콘텐츠 10개
            repeat(10) { index ->
                insertContentWithCategory(UUID.randomUUID(), creator1.id!!, "Content $index", Category.PROGRAMMING,
                    InteractionCounts(100, 10, 10, 10, 10))
            }

            // When: limit=5로 조회
            val result = feedRepository.findContentIdsByCategory(
                category = Category.PROGRAMMING,
                sortBy = me.onetwo.growsnap.domain.feed.model.CategoryFeedSortType.RECENT,
                cursor = null,
                limit = 5
            ).collectList().block()!!

            // Then: 5개만 반환
            assertEquals(5, result.size)
        }

        @Test
        @DisplayName("삭제된 콘텐츠는 조회되지 않는다")
        fun findContentIdsByCategory_ExcludesDeletedContent() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: PROGRAMMING 카테고리의 콘텐츠 2개 (1개는 삭제됨)
            val activeId = UUID.randomUUID()
            val deletedId = UUID.randomUUID()

            insertContentWithCategory(activeId, creator1.id!!, "Active", Category.PROGRAMMING,
                InteractionCounts(100, 10, 10, 10, 10))
            insertContentWithCategory(deletedId, creator1.id!!, "Deleted", Category.PROGRAMMING,
                InteractionCounts(200, 20, 20, 20, 20))

            // 콘텐츠 삭제 (Soft Delete)
            Mono.from(dslContext.update(CONTENTS)
                .set(CONTENTS.DELETED_AT, Instant.now())
                .where(CONTENTS.ID.eq(deletedId.toString()))).block()

            // When: PROGRAMMING 카테고리 조회
            val result = feedRepository.findContentIdsByCategory(
                category = Category.PROGRAMMING,
                sortBy = me.onetwo.growsnap.domain.feed.model.CategoryFeedSortType.POPULAR,
                cursor = null,
                limit = 10
            ).collectList().block()!!

            // Then: 삭제되지 않은 콘텐츠만 반환
            assertEquals(1, result.size)
            assertEquals(activeId, result[0])
        }

        @Test
        @DisplayName("해당 카테고리의 콘텐츠가 없으면, 빈 목록을 반환한다")
        fun findContentIdsByCategory_WithNoContent_ReturnsEmptyList() {
            // Given: setUp()의 기존 콘텐츠 삭제
            Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
            Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
            Mono.from(dslContext.deleteFrom(CONTENTS)).block()

            // Given: 다른 카테고리의 콘텐츠만 있음
            insertContentWithCategory(UUID.randomUUID(), creator1.id!!, "Art Content", Category.ART,
                InteractionCounts(100, 10, 10, 10, 10))

            // When: PROGRAMMING 카테고리 조회 (콘텐츠 없음)
            val result = feedRepository.findContentIdsByCategory(
                category = Category.PROGRAMMING,
                sortBy = me.onetwo.growsnap.domain.feed.model.CategoryFeedSortType.POPULAR,
                cursor = null,
                limit = 10
            ).collectList().block()!!

            // Then: 빈 목록 반환
            assertEquals(0, result.size)
        }

        /**
         * 특정 카테고리의 콘텐츠 + 인터랙션 정보 삽입 헬퍼 메서드
         */
        private fun insertContentWithCategory(
            contentId: UUID,
            creatorId: UUID,
            title: String,
            category: Category,
            interactions: InteractionCounts,
            createdAt: Instant = Instant.now()
        ) {
            // Contents 테이블
            Mono.from(dslContext.insertInto(CONTENTS)
                .set(CONTENTS.ID, contentId.toString())
                .set(CONTENTS.CREATOR_ID, creatorId.toString())
                .set(CONTENTS.CONTENT_TYPE, ContentType.VIDEO.name)
                .set(CONTENTS.URL, "https://example.com/$contentId.mp4")
                .set(CONTENTS.THUMBNAIL_URL, "https://example.com/$contentId-thumb.jpg")
                .set(CONTENTS.DURATION, 60)
                .set(CONTENTS.WIDTH, 1920)
                .set(CONTENTS.HEIGHT, 1080)
                .set(CONTENTS.STATUS, ContentStatus.PUBLISHED.name)
                .set(CONTENTS.CREATED_AT, createdAt)
                .set(CONTENTS.CREATED_BY, creatorId.toString())
                .set(CONTENTS.UPDATED_AT, createdAt)
                .set(CONTENTS.UPDATED_BY, creatorId.toString())).block()

            // Content_Metadata 테이블 (카테고리 지정)
            Mono.from(dslContext.insertInto(CONTENT_METADATA)
                .set(CONTENT_METADATA.CONTENT_ID, contentId.toString())
                .set(CONTENT_METADATA.TITLE, title)
                .set(CONTENT_METADATA.DESCRIPTION, "Test Description")
                .set(CONTENT_METADATA.CATEGORY, category.name)
                .set(CONTENT_METADATA.TAGS, JSON.valueOf("[\"test\", \"video\"]"))
                .set(CONTENT_METADATA.LANGUAGE, "ko")
                .set(CONTENT_METADATA.CREATED_AT, createdAt)
                .set(CONTENT_METADATA.CREATED_BY, creatorId.toString())
                .set(CONTENT_METADATA.UPDATED_AT, createdAt)
                .set(CONTENT_METADATA.UPDATED_BY, creatorId.toString())).block()

            // Content_Interactions 테이블
            Mono.from(dslContext.insertInto(CONTENT_INTERACTIONS)
                .set(CONTENT_INTERACTIONS.CONTENT_ID, contentId.toString())
                .set(CONTENT_INTERACTIONS.VIEW_COUNT, interactions.viewCount)
                .set(CONTENT_INTERACTIONS.LIKE_COUNT, interactions.likeCount)
                .set(CONTENT_INTERACTIONS.COMMENT_COUNT, interactions.commentCount)
                .set(CONTENT_INTERACTIONS.SAVE_COUNT, interactions.saveCount)
                .set(CONTENT_INTERACTIONS.SHARE_COUNT, interactions.shareCount)
                .set(CONTENT_INTERACTIONS.CREATED_AT, createdAt)
                .set(CONTENT_INTERACTIONS.CREATED_BY, creatorId.toString())
                .set(CONTENT_INTERACTIONS.UPDATED_AT, createdAt)
                .set(CONTENT_INTERACTIONS.UPDATED_BY, creatorId.toString())).block()
        }
    }
    */

    @Nested
    @DisplayName("findByContentIds - 콘텐츠 ID 목록으로 피드 조회")
    inner class FindByContentIds {

        @Test
        @DisplayName("여러 PHOTO 콘텐츠 조회 시, 각 콘텐츠의 photoUrls가 N+1 없이 반환된다")
        fun findByContentIds_WithMultiplePhotoContent_ReturnsPhotoUrlsWithoutNPlusOne() {
            // Given: 2개의 PHOTO 타입 콘텐츠 생성
            val photoContent1Id = UUID.randomUUID()
            val photoContent2Id = UUID.randomUUID()

            insertPhotoContent(
                contentId = photoContent1Id,
                creatorId = creator1.id!!,
                title = "Photo Content 1",
                createdAt = Instant.now().minus(1, ChronoUnit.HOURS)
            )
            insertContentPhoto(photoContent1Id, "https://example.com/content1-photo1.jpg", 0, creator1.id!!)
            insertContentPhoto(photoContent1Id, "https://example.com/content1-photo2.jpg", 1, creator1.id!!)

            insertPhotoContent(
                contentId = photoContent2Id,
                creatorId = creator2.id!!,
                title = "Photo Content 2",
                createdAt = Instant.now().minus(2, ChronoUnit.HOURS)
            )
            insertContentPhoto(photoContent2Id, "https://example.com/content2-photo1.jpg", 0, creator2.id!!)
            insertContentPhoto(photoContent2Id, "https://example.com/content2-photo2.jpg", 1, creator2.id!!)
            insertContentPhoto(photoContent2Id, "https://example.com/content2-photo3.jpg", 2, creator2.id!!)

            // When: 일괄 조회
            val result = feedRepository.findByContentIds(
                userId = viewer.id!!,
                contentIds = listOf(photoContent1Id, photoContent2Id)
            ).collectList().block()!!

            // Then: 각 콘텐츠의 photoUrls가 올바르게 반환됨 (N+1 없이 batch loading)
            assertEquals(2, result.size)

            val content1 = result.find { it.contentId == photoContent1Id }!!
            assertNotNull(content1.photoUrls)
            assertEquals(2, content1.photoUrls!!.size)
            assertEquals("https://example.com/content1-photo1.jpg", content1.photoUrls!![0])
            assertEquals("https://example.com/content1-photo2.jpg", content1.photoUrls!![1])

            val content2 = result.find { it.contentId == photoContent2Id }!!
            assertNotNull(content2.photoUrls)
            assertEquals(3, content2.photoUrls!!.size)
            assertEquals("https://example.com/content2-photo1.jpg", content2.photoUrls!![0])
            assertEquals("https://example.com/content2-photo2.jpg", content2.photoUrls!![1])
            assertEquals("https://example.com/content2-photo3.jpg", content2.photoUrls!![2])
        }

        @Test
        @DisplayName("VIDEO와 PHOTO 혼합 조회 시, PHOTO만 photoUrls를 가진다")
        fun findByContentIds_WithMixedContentTypes_ReturnsPhotoUrlsOnlyForPhoto() {
            // Given: VIDEO와 PHOTO 콘텐츠 생성
            val photoContentId = UUID.randomUUID()
            insertPhotoContent(
                contentId = photoContentId,
                creatorId = creator1.id!!,
                title = "Photo Content",
                createdAt = Instant.now()
            )
            insertContentPhoto(photoContentId, "https://example.com/photo.jpg", 0, creator1.id!!)

            // When: VIDEO와 PHOTO 혼합 조회
            val result = feedRepository.findByContentIds(
                userId = viewer.id!!,
                contentIds = listOf(content1Id, photoContentId) // content1Id는 VIDEO
            ).collectList().block()!!

            // Then: PHOTO는 photoUrls를 가지고, VIDEO는 null
            assertEquals(2, result.size)

            val videoContent = result.find { it.contentId == content1Id }!!
            assertNull(videoContent.photoUrls)

            val photoContent = result.find { it.contentId == photoContentId }!!
            assertNotNull(photoContent.photoUrls)
            assertEquals(1, photoContent.photoUrls!!.size)
        }

        @Test
        @DisplayName("PHOTO 콘텐츠의 사진이 없으면, photoUrls가 null이다")
        fun findByContentIds_WithPhotoContentButNoPhotos_ReturnsNullPhotoUrls() {
            // Given: 사진이 없는 PHOTO 타입 콘텐츠 생성
            val photoContentId = UUID.randomUUID()
            insertPhotoContent(
                contentId = photoContentId,
                creatorId = creator1.id!!,
                title = "Photo Content Without Photos",
                createdAt = Instant.now()
            )
            // 사진을 삽입하지 않음

            // When
            val result = feedRepository.findByContentIds(
                userId = viewer.id!!,
                contentIds = listOf(photoContentId)
            ).collectList().block()!!

            // Then: photoUrls가 null (사진이 없는 PHOTO 콘텐츠)
            assertEquals(1, result.size)
            val photoContent = result[0]
            assertNull(photoContent.photoUrls)
        }

        @Test
        @DisplayName("콘텐츠 ID 목록이 비어있으면, 빈 목록을 반환한다")
        fun findByContentIds_WithEmptyContentIds_ReturnsEmptyList() {
            // When: 빈 ID 목록으로 조회
            val result = feedRepository.findByContentIds(
                userId = viewer.id!!,
                contentIds = emptyList()
            ).collectList().block()!!

            // Then: 빈 목록 반환
            assertTrue(result.isEmpty())
        }
    }

    /**
     * 인터랙션 카운트 데이터 클래스
     *
     * Long Parameter List를 해결하기 위한 데이터 클래스
     */
    private data class InteractionCounts(
        val viewCount: Int = 0,
        val likeCount: Int = 0,
        val commentCount: Int = 0,
        val saveCount: Int = 0,
        val shareCount: Int = 0
    )

    /**
     * 콘텐츠 + 인터랙션 정보 삽입 헬퍼 메서드 (오버로드 - 하위 호환성 유지)
     */
    private fun insertContentWithInteractions(
        contentId: UUID,
        creatorId: UUID,
        title: String,
        viewCount: Int,
        likeCount: Int,
        commentCount: Int,
        saveCount: Int,
        shareCount: Int,
        createdAt: Instant = Instant.now()
    ) {
        insertContentWithInteractions(
            contentId,
            creatorId,
            title,
            InteractionCounts(viewCount, likeCount, commentCount, saveCount, shareCount),
            createdAt
        )
    }

    /**
     * 콘텐츠 + 인터랙션 정보 삽입 헬퍼 메서드 (새 버전 - 파라미터 간소화)
     */
    private fun insertContentWithInteractions(
        contentId: UUID,
        creatorId: UUID,
        title: String,
        interactions: InteractionCounts,
        createdAt: Instant = Instant.now()
    ) {
        // Contents 테이블
        Mono.from(dslContext.insertInto(CONTENTS)
            .set(CONTENTS.ID, contentId.toString())
            .set(CONTENTS.CREATOR_ID, creatorId.toString())
            .set(CONTENTS.CONTENT_TYPE, ContentType.VIDEO.name)
            .set(CONTENTS.URL, "https://example.com/$contentId.mp4")
            .set(CONTENTS.THUMBNAIL_URL, "https://example.com/$contentId-thumb.jpg")
            .set(CONTENTS.DURATION, 60)
            .set(CONTENTS.WIDTH, 1920)
            .set(CONTENTS.HEIGHT, 1080)
            .set(CONTENTS.STATUS, ContentStatus.PUBLISHED.name)
            .set(CONTENTS.CREATED_AT, createdAt)
            .set(CONTENTS.CREATED_BY, creatorId.toString())
            .set(CONTENTS.UPDATED_AT, createdAt)
            .set(CONTENTS.UPDATED_BY, creatorId.toString())).block()

        // Content_Metadata 테이블
        Mono.from(dslContext.insertInto(CONTENT_METADATA)
            .set(CONTENT_METADATA.CONTENT_ID, contentId.toString())
            .set(CONTENT_METADATA.TITLE, title)
            .set(CONTENT_METADATA.DESCRIPTION, "Test Description")
            .set(CONTENT_METADATA.CATEGORY, Category.PROGRAMMING.name)
            .set(CONTENT_METADATA.TAGS, JSON.valueOf("[\"test\", \"video\"]"))
            .set(CONTENT_METADATA.LANGUAGE, "ko")
            .set(CONTENT_METADATA.CREATED_AT, createdAt)
            .set(CONTENT_METADATA.CREATED_BY, creatorId.toString())
            .set(CONTENT_METADATA.UPDATED_AT, createdAt)
            .set(CONTENT_METADATA.UPDATED_BY, creatorId.toString())).block()

        // Content_Interactions 테이블 (커스텀 인터랙션 수)
        Mono.from(dslContext.insertInto(CONTENT_INTERACTIONS)
            .set(CONTENT_INTERACTIONS.CONTENT_ID, contentId.toString())
            .set(CONTENT_INTERACTIONS.VIEW_COUNT, interactions.viewCount)
            .set(CONTENT_INTERACTIONS.LIKE_COUNT, interactions.likeCount)
            .set(CONTENT_INTERACTIONS.COMMENT_COUNT, interactions.commentCount)
            .set(CONTENT_INTERACTIONS.SAVE_COUNT, interactions.saveCount)
            .set(CONTENT_INTERACTIONS.SHARE_COUNT, interactions.shareCount)
            .set(CONTENT_INTERACTIONS.CREATED_AT, createdAt)
            .set(CONTENT_INTERACTIONS.CREATED_BY, creatorId.toString())
            .set(CONTENT_INTERACTIONS.UPDATED_AT, createdAt)
            .set(CONTENT_INTERACTIONS.UPDATED_BY, creatorId.toString())).block()
    }

    /**
     * 콘텐츠 삽입 헬퍼 메서드
     */
    private fun insertContent(
        contentId: UUID,
        creatorId: UUID,
        title: String,
        createdAt: Instant
    ) {
        // Contents 테이블
        Mono.from(dslContext.insertInto(CONTENTS)
            .set(CONTENTS.ID, contentId.toString())
            .set(CONTENTS.CREATOR_ID, creatorId.toString())
            .set(CONTENTS.CONTENT_TYPE, ContentType.VIDEO.name)
            .set(CONTENTS.URL, "https://example.com/$contentId.mp4")
            .set(CONTENTS.THUMBNAIL_URL, "https://example.com/$contentId-thumb.jpg")
            .set(CONTENTS.DURATION, 60)
            .set(CONTENTS.WIDTH, 1920)
            .set(CONTENTS.HEIGHT, 1080)
            .set(CONTENTS.STATUS, ContentStatus.PUBLISHED.name)
            .set(CONTENTS.CREATED_AT, createdAt)
            .set(CONTENTS.CREATED_BY, creatorId.toString())
            .set(CONTENTS.UPDATED_AT, createdAt)
            .set(CONTENTS.UPDATED_BY, creatorId.toString())).block()

        // Content_Metadata 테이블
        Mono.from(dslContext.insertInto(CONTENT_METADATA)
            .set(CONTENT_METADATA.CONTENT_ID, contentId.toString())
            .set(CONTENT_METADATA.TITLE, title)
            .set(CONTENT_METADATA.DESCRIPTION, "Test Description")
            .set(CONTENT_METADATA.CATEGORY, Category.PROGRAMMING.name)
            .set(CONTENT_METADATA.TAGS, JSON.valueOf("[\"test\", \"video\"]"))
            .set(CONTENT_METADATA.LANGUAGE, "ko")
            .set(CONTENT_METADATA.CREATED_AT, createdAt)
            .set(CONTENT_METADATA.CREATED_BY, creatorId.toString())
            .set(CONTENT_METADATA.UPDATED_AT, createdAt)
            .set(CONTENT_METADATA.UPDATED_BY, creatorId.toString())).block()

        // Content_Interactions 테이블
        Mono.from(dslContext.insertInto(CONTENT_INTERACTIONS)
            .set(CONTENT_INTERACTIONS.CONTENT_ID, contentId.toString())
            .set(CONTENT_INTERACTIONS.LIKE_COUNT, 100)
            .set(CONTENT_INTERACTIONS.COMMENT_COUNT, 50)
            .set(CONTENT_INTERACTIONS.SAVE_COUNT, 30)
            .set(CONTENT_INTERACTIONS.SHARE_COUNT, 20)
            .set(CONTENT_INTERACTIONS.VIEW_COUNT, 1000)
            .set(CONTENT_INTERACTIONS.CREATED_AT, createdAt)
            .set(CONTENT_INTERACTIONS.CREATED_BY, creatorId.toString())
            .set(CONTENT_INTERACTIONS.UPDATED_AT, createdAt)
            .set(CONTENT_INTERACTIONS.UPDATED_BY, creatorId.toString())).block()
    }

    /**
     * 자막 삽입 헬퍼 메서드
     */
    private fun insertSubtitle(
        contentId: UUID,
        language: String,
        subtitleUrl: String
    ) {
        Mono.from(dslContext.insertInto(CONTENT_SUBTITLES)
            .set(CONTENT_SUBTITLES.CONTENT_ID, contentId.toString())
            .set(CONTENT_SUBTITLES.LANGUAGE, language)
            .set(CONTENT_SUBTITLES.SUBTITLE_URL, subtitleUrl)
            .set(CONTENT_SUBTITLES.CREATED_AT, Instant.now())
            .set(CONTENT_SUBTITLES.UPDATED_AT, Instant.now())).block()
    }

    /**
     * 시청 기록 삽입 헬퍼 메서드
     */
    private fun insertViewHistory(
        userId: UUID,
        contentId: UUID,
        watchedAt: Instant
    ) {
        Mono.from(dslContext.insertInto(USER_VIEW_HISTORY)
            .set(USER_VIEW_HISTORY.USER_ID, userId.toString())
            .set(USER_VIEW_HISTORY.CONTENT_ID, contentId.toString())
            .set(USER_VIEW_HISTORY.WATCHED_AT, watchedAt)
            .set(USER_VIEW_HISTORY.COMPLETION_RATE, 100)
            .set(USER_VIEW_HISTORY.CREATED_AT, watchedAt)
            .set(USER_VIEW_HISTORY.CREATED_BY, userId.toString())
            .set(USER_VIEW_HISTORY.UPDATED_AT, watchedAt)
            .set(USER_VIEW_HISTORY.UPDATED_BY, userId.toString())).block()
    }

    /**
     * PHOTO 타입 콘텐츠 삽입 헬퍼 메서드
     */
    private fun insertPhotoContent(
        contentId: UUID,
        creatorId: UUID,
        title: String,
        createdAt: Instant
    ) {
        // Contents 테이블 (PHOTO 타입)
        Mono.from(dslContext.insertInto(CONTENTS)
            .set(CONTENTS.ID, contentId.toString())
            .set(CONTENTS.CREATOR_ID, creatorId.toString())
            .set(CONTENTS.CONTENT_TYPE, ContentType.PHOTO.name)
            .set(CONTENTS.URL, "https://example.com/$contentId-cover.jpg") // 대표 이미지
            .set(CONTENTS.THUMBNAIL_URL, "https://example.com/$contentId-thumb.jpg")
            // PHOTO는 duration 없음 (null이 기본값)
            .set(CONTENTS.WIDTH, 1080)
            .set(CONTENTS.HEIGHT, 1080)
            .set(CONTENTS.STATUS, ContentStatus.PUBLISHED.name)
            .set(CONTENTS.CREATED_AT, createdAt)
            .set(CONTENTS.CREATED_BY, creatorId.toString())
            .set(CONTENTS.UPDATED_AT, createdAt)
            .set(CONTENTS.UPDATED_BY, creatorId.toString())).block()

        // Content_Metadata 테이블
        Mono.from(dslContext.insertInto(CONTENT_METADATA)
            .set(CONTENT_METADATA.CONTENT_ID, contentId.toString())
            .set(CONTENT_METADATA.TITLE, title)
            .set(CONTENT_METADATA.DESCRIPTION, "Test Photo Description")
            .set(CONTENT_METADATA.CATEGORY, Category.ART.name)
            .set(CONTENT_METADATA.TAGS, JSON.valueOf("[\"test\", \"photo\"]"))
            .set(CONTENT_METADATA.LANGUAGE, "ko")
            .set(CONTENT_METADATA.CREATED_AT, createdAt)
            .set(CONTENT_METADATA.CREATED_BY, creatorId.toString())
            .set(CONTENT_METADATA.UPDATED_AT, createdAt)
            .set(CONTENT_METADATA.UPDATED_BY, creatorId.toString())).block()

        // Content_Interactions 테이블
        Mono.from(dslContext.insertInto(CONTENT_INTERACTIONS)
            .set(CONTENT_INTERACTIONS.CONTENT_ID, contentId.toString())
            .set(CONTENT_INTERACTIONS.LIKE_COUNT, 100)
            .set(CONTENT_INTERACTIONS.COMMENT_COUNT, 50)
            .set(CONTENT_INTERACTIONS.SAVE_COUNT, 30)
            .set(CONTENT_INTERACTIONS.SHARE_COUNT, 20)
            .set(CONTENT_INTERACTIONS.VIEW_COUNT, 1000)
            .set(CONTENT_INTERACTIONS.CREATED_AT, createdAt)
            .set(CONTENT_INTERACTIONS.CREATED_BY, creatorId.toString())
            .set(CONTENT_INTERACTIONS.UPDATED_AT, createdAt)
            .set(CONTENT_INTERACTIONS.UPDATED_BY, creatorId.toString())).block()
    }

    /**
     * 콘텐츠 사진 삽입 헬퍼 메서드
     */
    private fun insertContentPhoto(
        contentId: UUID,
        photoUrl: String,
        displayOrder: Int,
        createdBy: UUID
    ) {
        Mono.from(dslContext.insertInto(CONTENT_PHOTOS)
            .set(CONTENT_PHOTOS.CONTENT_ID, contentId.toString())
            .set(CONTENT_PHOTOS.PHOTO_URL, photoUrl)
            .set(CONTENT_PHOTOS.DISPLAY_ORDER, displayOrder)
            .set(CONTENT_PHOTOS.WIDTH, 1080)
            .set(CONTENT_PHOTOS.HEIGHT, 1080)
            .set(CONTENT_PHOTOS.CREATED_AT, Instant.now())
            .set(CONTENT_PHOTOS.CREATED_BY, createdBy.toString())
            .set(CONTENT_PHOTOS.UPDATED_AT, Instant.now())
            .set(CONTENT_PHOTOS.UPDATED_BY, createdBy.toString())).block()
    }
}
