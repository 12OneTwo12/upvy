package me.onetwo.growsnap.domain.analytics.repository

import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENTS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_INTERACTIONS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_METADATA
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_PHOTOS
import me.onetwo.growsnap.jooq.generated.tables.references.USER_PROFILES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY
import me.onetwo.growsnap.jooq.generated.tables.references.USERS
import org.jooq.DSLContext
import org.jooq.JSON
import reactor.core.publisher.Mono
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * UserViewHistoryRepository 통합 테스트
 *
 * 실제 데이터베이스(H2)를 사용하여 시청 기록 관리 기능을 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("사용자 시청 기록 Repository 통합 테스트")
class UserViewHistoryRepositoryTest {

    @Autowired
    private lateinit var userViewHistoryRepository: UserViewHistoryRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    private lateinit var testUser: User
    private lateinit var testContent1Id: UUID
    private lateinit var testContent2Id: UUID
    private lateinit var testContent3Id: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트 데이터 준비

        // 사용자 생성
        testUser = userRepository.save(
            User(
                email = "user@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "user-123",
                role = UserRole.USER
            )
        ).block()!!

        // 콘텐츠 3개 생성
        testContent1Id = UUID.randomUUID()
        testContent2Id = UUID.randomUUID()
        testContent3Id = UUID.randomUUID()

        insertContent(testContent1Id, testUser.id!!, "Content 1")
        insertContent(testContent2Id, testUser.id!!, "Content 2")
        insertContent(testContent3Id, testUser.id!!, "Content 3")
    }

    @AfterEach
    fun tearDown() {
        Mono.from(dslContext.deleteFrom(USER_VIEW_HISTORY)).block()
        Mono.from(dslContext.deleteFrom(CONTENT_PHOTOS)).block()
        Mono.from(dslContext.deleteFrom(CONTENTS)).block()
        Mono.from(dslContext.deleteFrom(USER_PROFILES)).block()
        Mono.from(dslContext.deleteFrom(USERS)).block()
    }

    @Nested
    @DisplayName("save - 시청 기록 저장")
    inner class Save {

        @Test
        @DisplayName("시청 기록을 저장한다")
        fun save_CreatesViewHistory() {
            // When: 시청 기록 저장
            userViewHistoryRepository.save(
                testUser.id!!,
                testContent1Id,
                watchedDuration = 120,
                completionRate = 75
            ).block()

            // Then: 저장 확인
            val result = userViewHistoryRepository.findRecentViewedContentIds(
                testUser.id!!,
                Instant.now().minus(1, ChronoUnit.DAYS),
                10
            ).collectList().block()!!

            assertEquals(1, result.size)
            assertEquals(testContent1Id, result[0])
        }

        @Test
        @DisplayName("여러 시청 기록을 저장한다")
        fun save_MultipleRecords_CreatesAll() {
            // When: 여러 시청 기록 저장
            userViewHistoryRepository.save(
                testUser.id!!,
                testContent1Id,
                watchedDuration = 100,
                completionRate = 80
            ).block()

            userViewHistoryRepository.save(
                testUser.id!!,
                testContent2Id,
                watchedDuration = 200,
                completionRate = 100
            ).block()

            // Then: 모두 저장 확인
            val result = userViewHistoryRepository.findRecentViewedContentIds(
                testUser.id!!,
                Instant.now().minus(1, ChronoUnit.DAYS),
                10
            ).collectList().block()!!

            assertEquals(2, result.size)
        }

        @Test
        @DisplayName("같은 콘텐츠를 여러 번 시청하면 별도로 저장된다")
        fun save_SameContentMultipleTimes_CreatesMultipleRecords() {
            // When: 같은 콘텐츠를 2번 시청
            userViewHistoryRepository.save(
                testUser.id!!,
                testContent1Id,
                watchedDuration = 100,
                completionRate = 50
            ).block()

            // created_at은 millisecond 단위이므로 시간 차이 자동 보장

            userViewHistoryRepository.save(
                testUser.id!!,
                testContent1Id,
                watchedDuration = 150,
                completionRate = 100
            ).block()

            // Then: 2개의 기록 존재
            val result = userViewHistoryRepository.findRecentViewHistoryDetails(
                testUser.id!!,
                Instant.now().minus(1, ChronoUnit.DAYS),
                10
            ).collectList().block()!!

            assertEquals(2, result.size)
            assertEquals(testContent1Id, result[0].contentId)
            assertEquals(testContent1Id, result[1].contentId)
        }
    }

    @Nested
    @DisplayName("findRecentViewedContentIds - 최근 시청한 콘텐츠 ID 조회")
    inner class FindRecentViewedContentIds {

        @Test
        @DisplayName("최근 시청한 콘텐츠 ID를 반환한다")
        fun findRecentViewedContentIds_ReturnsRecentContents() {
            // Given: 시청 기록 저장
            val now = Instant.now()
            saveViewHistory(testUser.id!!, testContent1Id, now.minus(3, ChronoUnit.HOURS))
            saveViewHistory(testUser.id!!, testContent2Id, now.minus(2, ChronoUnit.HOURS))
            saveViewHistory(testUser.id!!, testContent3Id, now.minus(1, ChronoUnit.HOURS))

            // When: 최근 시청 콘텐츠 조회
            val result = userViewHistoryRepository.findRecentViewedContentIds(
                testUser.id!!,
                now.minus(1, ChronoUnit.DAYS),
                10
            ).collectList().block()!!

            // Then: 최신순으로 정렬되어 반환
            assertEquals(3, result.size)
            assertEquals(testContent3Id, result[0])  // 가장 최근
            assertEquals(testContent2Id, result[1])
            assertEquals(testContent1Id, result[2])
        }

        @Test
        @DisplayName("since 이전의 기록은 제외된다")
        fun findRecentViewedContentIds_BeforeSince_Excluded() {
            // Given: 오래된 기록과 최근 기록
            val now = Instant.now()
            saveViewHistory(testUser.id!!, testContent1Id, now.minus(10, ChronoUnit.DAYS))  // 오래됨
            saveViewHistory(testUser.id!!, testContent2Id, now.minus(1, ChronoUnit.HOURS))  // 최근

            // When: 최근 3일 이내 기록만 조회
            val result = userViewHistoryRepository.findRecentViewedContentIds(
                testUser.id!!,
                now.minus(3, ChronoUnit.DAYS),
                10
            ).collectList().block()!!

            // Then: content2만 반환
            assertEquals(1, result.size)
            assertEquals(testContent2Id, result[0])
        }

        @Test
        @DisplayName("limit만큼만 반환한다")
        fun findRecentViewedContentIds_WithLimit_ReturnsLimitedResults() {
            // Given: 5개 시청 기록
            val now = Instant.now()
            val contentIds = List(5) { UUID.randomUUID() }

            // 콘텐츠 생성
            contentIds.forEach { contentId ->
                insertContent(contentId, testUser.id!!, "Test Content ${contentId}")
            }

            // 시청 기록 생성
            contentIds.forEachIndexed { index, contentId ->
                saveViewHistory(testUser.id!!, contentId, now.minus(index.toLong(), ChronoUnit.HOURS))
            }

            // When: limit=3
            val result = userViewHistoryRepository.findRecentViewedContentIds(
                testUser.id!!,
                now.minus(1, ChronoUnit.DAYS),
                3
            ).collectList().block()!!

            // Then: 3개만 반환
            assertEquals(3, result.size)
        }

        @Test
        @DisplayName("시청 기록이 없으면 빈 목록을 반환한다")
        fun findRecentViewedContentIds_NoHistory_ReturnsEmptyList() {
            // When: 시청 기록 없이 조회
            val result = userViewHistoryRepository.findRecentViewedContentIds(
                testUser.id!!,
                Instant.now().minus(1, ChronoUnit.DAYS),
                10
            ).collectList().block()!!

            // Then: 빈 목록
            assertEquals(0, result.size)
        }
    }

    @Nested
    @DisplayName("findRecentViewHistoryDetails - 최근 시청 기록 상세 정보 조회")
    inner class FindRecentViewHistoryDetails {

        @Test
        @DisplayName("시청 기록 상세 정보를 반환한다")
        fun findRecentViewHistoryDetails_ReturnsDetailedHistory() {
            // Given: 시청 기록 저장
            val now = Instant.now()
            userViewHistoryRepository.save(
                testUser.id!!,
                testContent1Id,
                watchedDuration = 120,
                completionRate = 80
            ).block()

            // When: 상세 정보 조회
            val result = userViewHistoryRepository.findRecentViewHistoryDetails(
                testUser.id!!,
                now.minus(1, ChronoUnit.DAYS),
                10
            ).collectList().block()!!

            // Then: 상세 정보 확인
            assertEquals(1, result.size)
            val detail = result[0]
            assertEquals(testContent1Id, detail.contentId)
            assertEquals(120, detail.watchedDuration)
            assertEquals(80, detail.completionRate)
            assertTrue(detail.watchedAt.isAfter(now.minus(1, ChronoUnit.MINUTES)))
        }

        @Test
        @DisplayName("최신순으로 정렬되어 반환한다")
        fun findRecentViewHistoryDetails_OrderedByWatchedAtDesc() {
            // Given: 여러 시청 기록
            val now = Instant.now()
            saveViewHistory(testUser.id!!, testContent1Id, now.minus(3, ChronoUnit.HOURS), 100, 50)
            saveViewHistory(testUser.id!!, testContent2Id, now.minus(2, ChronoUnit.HOURS), 200, 75)
            saveViewHistory(testUser.id!!, testContent3Id, now.minus(1, ChronoUnit.HOURS), 300, 100)

            // When: 상세 정보 조회
            val result = userViewHistoryRepository.findRecentViewHistoryDetails(
                testUser.id!!,
                now.minus(1, ChronoUnit.DAYS),
                10
            ).collectList().block()!!

            // Then: 최신순 정렬
            assertEquals(3, result.size)
            assertEquals(testContent3Id, result[0].contentId)  // 가장 최근
            assertEquals(300, result[0].watchedDuration)
            assertEquals(100, result[0].completionRate)

            assertEquals(testContent2Id, result[1].contentId)
            assertEquals(200, result[1].watchedDuration)
            assertEquals(75, result[1].completionRate)

            assertEquals(testContent1Id, result[2].contentId)
            assertEquals(100, result[2].watchedDuration)
            assertEquals(50, result[2].completionRate)
        }

        @Test
        @DisplayName("since 이전의 기록은 제외된다")
        fun findRecentViewHistoryDetails_BeforeSince_Excluded() {
            // Given: 오래된 기록과 최근 기록
            val now = Instant.now()
            saveViewHistory(testUser.id!!, testContent1Id, now.minus(10, ChronoUnit.DAYS), 100, 50)
            saveViewHistory(testUser.id!!, testContent2Id, now.minus(1, ChronoUnit.HOURS), 200, 100)

            // When: 최근 3일 이내 기록만 조회
            val result = userViewHistoryRepository.findRecentViewHistoryDetails(
                testUser.id!!,
                now.minus(3, ChronoUnit.DAYS),
                10
            ).collectList().block()!!

            // Then: content2만 반환
            assertEquals(1, result.size)
            assertEquals(testContent2Id, result[0].contentId)
        }

        @Test
        @DisplayName("limit만큼만 반환한다")
        fun findRecentViewHistoryDetails_WithLimit_ReturnsLimitedResults() {
            // Given: 5개 시청 기록
            val now = Instant.now()
            val contentIds = List(5) { UUID.randomUUID() }

            // 콘텐츠 생성
            contentIds.forEach { contentId ->
                insertContent(contentId, testUser.id!!, "Test Content ${contentId}")
            }

            // 시청 기록 생성
            contentIds.forEachIndexed { index, contentId ->
                saveViewHistory(testUser.id!!, contentId, now.minus(index.toLong(), ChronoUnit.HOURS), 100, 50)
            }

            // When: limit=3
            val result = userViewHistoryRepository.findRecentViewHistoryDetails(
                testUser.id!!,
                now.minus(1, ChronoUnit.DAYS),
                3
            ).collectList().block()!!

            // Then: 3개만 반환
            assertEquals(3, result.size)
        }
    }

    /**
     * 콘텐츠 삽입 헬퍼 메서드
     */
    private fun insertContent(
        contentId: UUID,
        creatorId: UUID,
        title: String
    ) {
        val now = Instant.now()

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
            .set(CONTENTS.CREATED_AT, now)
            .set(CONTENTS.CREATED_BY, creatorId.toString())
            .set(CONTENTS.UPDATED_AT, now)
            .set(CONTENTS.UPDATED_BY, creatorId.toString())).block()

        // Content_Metadata 테이블
        Mono.from(dslContext.insertInto(CONTENT_METADATA)
            .set(CONTENT_METADATA.CONTENT_ID, contentId.toString())
            .set(CONTENT_METADATA.TITLE, title)
            .set(CONTENT_METADATA.DESCRIPTION, "Test Description")
            .set(CONTENT_METADATA.CATEGORY, Category.PROGRAMMING.name)
            .set(CONTENT_METADATA.TAGS, JSON.valueOf("[\"test\"]"))
            .set(CONTENT_METADATA.LANGUAGE, "ko")
            .set(CONTENT_METADATA.CREATED_AT, now)
            .set(CONTENT_METADATA.CREATED_BY, creatorId.toString())
            .set(CONTENT_METADATA.UPDATED_AT, now)
            .set(CONTENT_METADATA.UPDATED_BY, creatorId.toString())).block()

        // Content_Interactions 테이블
        Mono.from(dslContext.insertInto(CONTENT_INTERACTIONS)
            .set(CONTENT_INTERACTIONS.CONTENT_ID, contentId.toString())
            .set(CONTENT_INTERACTIONS.VIEW_COUNT, 0)
            .set(CONTENT_INTERACTIONS.LIKE_COUNT, 0)
            .set(CONTENT_INTERACTIONS.COMMENT_COUNT, 0)
            .set(CONTENT_INTERACTIONS.SAVE_COUNT, 0)
            .set(CONTENT_INTERACTIONS.SHARE_COUNT, 0)
            .set(CONTENT_INTERACTIONS.CREATED_AT, now)
            .set(CONTENT_INTERACTIONS.CREATED_BY, creatorId.toString())
            .set(CONTENT_INTERACTIONS.UPDATED_AT, now)
            .set(CONTENT_INTERACTIONS.UPDATED_BY, creatorId.toString())).block()
    }

    /**
     * 시청 기록 저장 헬퍼 메서드
     */
    private fun saveViewHistory(
        userId: UUID,
        contentId: UUID,
        watchedAt: Instant,
        watchedDuration: Int = 100,
        completionRate: Int = 50
    ) {
        // DSLContext를 사용하여 특정 watched_at 시간으로 저장
        Mono.from(dslContext.insertInto(me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY)
            .set(me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY.USER_ID, userId.toString())
            .set(me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY.CONTENT_ID, contentId.toString())
            .set(me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY.WATCHED_AT, watchedAt)
            .set(me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY.WATCHED_DURATION, watchedDuration)
            .set(me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY.COMPLETION_RATE, completionRate)
            .set(me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY.CREATED_AT, watchedAt)
            .set(me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY.CREATED_BY, userId.toString())
            .set(me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY.UPDATED_AT, watchedAt)
            .set(me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY.UPDATED_BY, userId.toString())).block()
    }
}
