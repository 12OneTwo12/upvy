package me.onetwo.growsnap.domain.feed.repository

import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_BLOCKS
import me.onetwo.growsnap.jooq.generated.tables.references.FOLLOWS
import me.onetwo.growsnap.jooq.generated.tables.references.USER_BLOCKS
import me.onetwo.growsnap.util.createContent
import me.onetwo.growsnap.util.createUserWithProfile
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FeedRepositoryImpl 통합 테스트
 *
 * 차단 기능 필터링을 검증합니다.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TestSecurityConfig::class)
class FeedRepositoryImplIntegrationTest {

    @Autowired
    private lateinit var feedRepository: FeedRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var contentRepository: ContentRepository

    @Autowired
    private lateinit var contentInteractionRepository: ContentInteractionRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    private lateinit var userAId: UUID
    private lateinit var userBId: UUID
    private lateinit var userCId: UUID
    private lateinit var contentB1Id: UUID
    private lateinit var contentB2Id: UUID
    private lateinit var contentC1Id: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트 사용자 생성 (고유한 이메일과 닉네임 사용)
        val testId = UUID.randomUUID().toString().substring(0, 8)

        val (userA, _) = createUserWithProfile(
            userRepository = userRepository,
            userProfileRepository = userProfileRepository,
            email = "userA-$testId@example.com",
            nickname = "userA-$testId"
        )
        userAId = userA.id!!

        val (userB, _) = createUserWithProfile(
            userRepository = userRepository,
            userProfileRepository = userProfileRepository,
            email = "userB-$testId@example.com",
            nickname = "userB-$testId"
        )
        userBId = userB.id!!

        val (userC, _) = createUserWithProfile(
            userRepository = userRepository,
            userProfileRepository = userProfileRepository,
            email = "userC-$testId@example.com",
            nickname = "userC-$testId"
        )
        userCId = userC.id!!

        // Given: 콘텐츠 생성
        val contentB1 = createContent(
            contentRepository = contentRepository,
            creatorId = userBId,
            title = "UserB Content 1",
            category = Category.PROGRAMMING,
            contentInteractionRepository = contentInteractionRepository
        )
        contentB1Id = contentB1.id!!

        val contentB2 = createContent(
            contentRepository = contentRepository,
            creatorId = userBId,
            title = "UserB Content 2",
            category = Category.DESIGN,
            contentInteractionRepository = contentInteractionRepository
        )
        contentB2Id = contentB2.id!!

        val contentC1 = createContent(
            contentRepository = contentRepository,
            creatorId = userCId,
            title = "UserC Content 1",
            category = Category.LANGUAGE,
            contentInteractionRepository = contentInteractionRepository
        )
        contentC1Id = contentC1.id!!
    }

    @AfterEach
    fun tearDown() {
        // Then: 테스트 데이터 정리 (R2DBC 사용 시 reactive 방식으로 처리)
        // 모든 데이터를 명시적으로 삭제 (foreign key 순서 고려)
        Mono.from(dslContext.deleteFrom(CONTENT_BLOCKS).where(CONTENT_BLOCKS.USER_ID.eq(userAId.toString())))
            .then(Mono.from(dslContext.deleteFrom(USER_BLOCKS).where(USER_BLOCKS.BLOCKER_ID.eq(userAId.toString()))))
            .then(Mono.from(dslContext.deleteFrom(FOLLOWS).where(FOLLOWS.FOLLOWER_ID.eq(userAId.toString()))))
            .block()
    }

    @Test
    @DisplayName("차단한 사용자의 콘텐츠는 팔로잉 피드에서 조회되지 않는다")
    fun `should not show blocked user content in following feed`() {
        // Given: User A가 User B를 팔로우
        Mono.from(dslContext.insertInto(FOLLOWS)
            .set(FOLLOWS.FOLLOWER_ID, userAId.toString())
            .set(FOLLOWS.FOLLOWING_ID, userBId.toString()))
            .block()

        // Given: User A가 User C를 팔로우
        Mono.from(dslContext.insertInto(FOLLOWS)
            .set(FOLLOWS.FOLLOWER_ID, userAId.toString())
            .set(FOLLOWS.FOLLOWING_ID, userCId.toString()))
            .block()

        // Given: User A가 User B를 차단
        Mono.from(dslContext.insertInto(USER_BLOCKS)
            .set(USER_BLOCKS.BLOCKER_ID, userAId.toString())
            .set(USER_BLOCKS.BLOCKED_ID, userBId.toString()))
            .block()

        // When: User A가 팔로잉 피드 조회
        val result = feedRepository.findFollowingFeed(
            userId = userAId,
            cursor = null,
            limit = 20
        )

        // Then: 팔로우했지만 차단한 User B의 콘텐츠는 포함되지 않음
        StepVerifier.create(result.collectList())
            .expectNextMatches { feeds ->
                // User B의 콘텐츠가 없어야 함 (팔로우했지만 차단함)
                feeds.none { it.contentId == contentB1Id } &&
                feeds.none { it.contentId == contentB2Id } &&
                // User C의 콘텐츠는 있어야 함 (팔로우하고 차단하지 않음)
                feeds.any { it.contentId == contentC1Id }
            }
            .verifyComplete()
    }

    @Test
    @DisplayName("차단한 콘텐츠는 팔로잉 피드에서 조회되지 않는다")
    fun `should not show blocked content in following feed`() {
        // Given: User A가 User B를 팔로우
        Mono.from(dslContext.insertInto(FOLLOWS)
            .set(FOLLOWS.FOLLOWER_ID, userAId.toString())
            .set(FOLLOWS.FOLLOWING_ID, userBId.toString()))
            .block()

        // Given: User A가 User B의 특정 콘텐츠를 차단
        Mono.from(dslContext.insertInto(CONTENT_BLOCKS)
            .set(CONTENT_BLOCKS.USER_ID, userAId.toString())
            .set(CONTENT_BLOCKS.CONTENT_ID, contentB1Id.toString()))
            .block()

        // When: User A가 팔로잉 피드 조회
        val result = feedRepository.findFollowingFeed(
            userId = userAId,
            cursor = null,
            limit = 20
        )

        // Then: 차단한 콘텐츠는 포함되지 않음
        StepVerifier.create(result.collectList())
            .expectNextMatches { feeds ->
                // 차단한 콘텐츠는 없어야 함
                feeds.none { it.contentId == contentB1Id } &&
                // 차단하지 않은 User B의 다른 콘텐츠는 있어야 함
                feeds.any { it.contentId == contentB2Id }
            }
            .verifyComplete()
    }

    @Test
    @DisplayName("차단하지 않은 콘텐츠는 팔로잉 피드에서 정상적으로 조회된다")
    fun `should show non-blocked content in following feed normally`() {
        // Given: User A가 User B를 팔로우
        Mono.from(dslContext.insertInto(FOLLOWS)
            .set(FOLLOWS.FOLLOWER_ID, userAId.toString())
            .set(FOLLOWS.FOLLOWING_ID, userBId.toString()))
            .block()

        // Given: User A는 아무도 차단하지 않음

        // When: User A가 팔로잉 피드 조회
        val result = feedRepository.findFollowingFeed(
            userId = userAId,
            cursor = null,
            limit = 20
        )

        // Then: 팔로우한 User B의 모든 콘텐츠가 정상적으로 조회됨
        StepVerifier.create(result.collectList())
            .expectNextMatches { feeds ->
                feeds.any { it.contentId == contentB1Id } &&
                feeds.any { it.contentId == contentB2Id }
            }
            .verifyComplete()
    }

    // ==================== findByContentIds 차단 테스트 ====================

    @Test
    @DisplayName("findByContentIds - 차단한 사용자의 콘텐츠는 제외된다")
    fun findByContentIds_WithBlockedUser_ExcludesBlockedUserContent() {
        // Given: userA가 userB를 차단
        blockUser(blockerId = userAId, blockedId = userBId)

        // When: contentB1, contentB2, contentC1을 모두 요청
        val result = feedRepository.findByContentIds(
            userId = userAId,
            contentIds = listOf(contentB1Id, contentB2Id, contentC1Id)
        ).collectList().block()!!

        // Then: userB의 contentB1과 contentB2는 제외되고, userC의 contentC1만 반환
        assertEquals(1, result.size)
        assertEquals(contentC1Id, result[0].contentId)
    }

    @Test
    @DisplayName("findByContentIds - 차단한 콘텐츠는 제외된다")
    fun findByContentIds_WithBlockedContent_ExcludesBlockedContent() {
        // Given: userA가 contentB1을 차단
        blockContent(userId = userAId, contentId = contentB1Id)

        // When: contentB1, contentB2, contentC1을 모두 요청
        val result = feedRepository.findByContentIds(
            userId = userAId,
            contentIds = listOf(contentB1Id, contentB2Id, contentC1Id)
        ).collectList().block()!!

        // Then: contentB1은 제외되고, contentB2와 contentC1만 반환
        assertEquals(2, result.size)
        assertFalse(result.any { it.contentId == contentB1Id })
    }

    // ==================== findPopularContentIds 차단 테스트 ====================

    @Test
    @DisplayName("findPopularContentIds - 차단한 사용자의 콘텐츠는 제외된다")
    fun findPopularContentIds_WithBlockedUser_ExcludesBlockedUserContent() {
        // Given: userA가 userB를 차단
        blockUser(blockerId = userAId, blockedId = userBId)

        // When: 인기 콘텐츠 조회
        val result = feedRepository.findPopularContentIds(
            userId = userAId,
            limit = 10,
            excludeIds = emptyList(),
            category = null
        ).collectList().block()!!

        // Then: userB의 콘텐츠는 제외됨
        assertFalse(result.contains(contentB1Id))
        assertFalse(result.contains(contentB2Id))
        assertTrue(result.contains(contentC1Id))
    }

    @Test
    @DisplayName("findPopularContentIds - 차단한 콘텐츠는 제외된다")
    fun findPopularContentIds_WithBlockedContent_ExcludesBlockedContent() {
        // Given: userA가 contentB1을 차단
        blockContent(userId = userAId, contentId = contentB1Id)

        // When: 인기 콘텐츠 조회
        val result = feedRepository.findPopularContentIds(
            userId = userAId,
            limit = 10,
            excludeIds = emptyList(),
            category = null
        ).collectList().block()!!

        // Then: contentB1은 제외됨
        assertFalse(result.contains(contentB1Id))
    }

    // ==================== findNewContentIds 차단 테스트 ====================

    @Test
    @DisplayName("findNewContentIds - 차단한 사용자의 콘텐츠는 제외된다")
    fun findNewContentIds_WithBlockedUser_ExcludesBlockedUserContent() {
        // Given: userA가 userB를 차단
        blockUser(blockerId = userAId, blockedId = userBId)

        // When: 신규 콘텐츠 조회
        val result = feedRepository.findNewContentIds(
            userId = userAId,
            limit = 10,
            excludeIds = emptyList(),
            category = null
        ).collectList().block()!!

        // Then: userB의 콘텐츠는 제외됨
        assertFalse(result.contains(contentB1Id))
        assertFalse(result.contains(contentB2Id))
    }

    @Test
    @DisplayName("findNewContentIds - 차단한 콘텐츠는 제외된다")
    fun findNewContentIds_WithBlockedContent_ExcludesBlockedContent() {
        // Given: userA가 contentB1을 차단
        blockContent(userId = userAId, contentId = contentB1Id)

        // When: 신규 콘텐츠 조회
        val result = feedRepository.findNewContentIds(
            userId = userAId,
            limit = 10,
            excludeIds = emptyList(),
            category = null
        ).collectList().block()!!

        // Then: contentB1은 제외됨
        assertFalse(result.contains(contentB1Id))
    }

    // ==================== findRandomContentIds 차단 테스트 ====================

    @Test
    @DisplayName("findRandomContentIds - 차단한 사용자의 콘텐츠는 제외된다")
    fun findRandomContentIds_WithBlockedUser_ExcludesBlockedUserContent() {
        // Given: userA가 userB를 차단
        blockUser(blockerId = userAId, blockedId = userBId)

        // When: 랜덤 콘텐츠 조회 (여러 번 시도하여 안정성 확보)
        val results = (1..5).map {
            feedRepository.findRandomContentIds(
                userId = userAId,
                limit = 10,
                excludeIds = emptyList(),
                category = null
            ).collectList().block()!!
        }.flatten()

        // Then: userB의 콘텐츠는 한 번도 나오지 않음
        assertFalse(results.contains(contentB1Id))
        assertFalse(results.contains(contentB2Id))
    }

    @Test
    @DisplayName("findRandomContentIds - 차단한 콘텐츠는 제외된다")
    fun findRandomContentIds_WithBlockedContent_ExcludesBlockedContent() {
        // Given: userA가 contentB1을 차단
        blockContent(userId = userAId, contentId = contentB1Id)

        // When: 랜덤 콘텐츠 조회 (여러 번 시도)
        val results = (1..5).map {
            feedRepository.findRandomContentIds(
                userId = userAId,
                limit = 10,
                excludeIds = emptyList(),
                category = null
            ).collectList().block()!!
        }.flatten()

        // Then: contentB1은 한 번도 나오지 않음
        assertFalse(results.contains(contentB1Id))
    }

    // ==================== findFollowingContentIdsByCategory 차단 테스트 ====================

    @Test
    @DisplayName("findFollowingContentIdsByCategory - 차단한 사용자의 콘텐츠는 제외된다")
    fun findFollowingContentIdsByCategory_WithBlockedUser_ExcludesBlockedUserContent() {
        // Given: userA가 userB, userC를 팔로우
        follow(followerId = userAId, followingId = userBId)
        follow(followerId = userAId, followingId = userCId)

        // And: userA가 userB를 차단
        blockUser(blockerId = userAId, blockedId = userBId)

        // When: PROGRAMMING 카테고리의 팔로잉 콘텐츠 조회
        val result = feedRepository.findFollowingContentIdsByCategory(
            userId = userAId,
            category = Category.PROGRAMMING,
            limit = 10,
            excludeIds = emptyList()
        ).collectList().block()!!

        // Then: userB의 콘텐츠는 제외됨
        assertFalse(result.contains(contentB1Id))
        assertFalse(result.contains(contentB2Id))
    }

    @Test
    @DisplayName("findFollowingContentIdsByCategory - 차단한 콘텐츠는 제외된다")
    fun findFollowingContentIdsByCategory_WithBlockedContent_ExcludesBlockedContent() {
        // Given: userA가 userB, userC를 팔로우
        follow(followerId = userAId, followingId = userBId)
        follow(followerId = userAId, followingId = userCId)

        // And: userA가 contentB1을 차단
        blockContent(userId = userAId, contentId = contentB1Id)

        // When: PROGRAMMING 카테고리의 팔로잉 콘텐츠 조회
        val result = feedRepository.findFollowingContentIdsByCategory(
            userId = userAId,
            category = Category.PROGRAMMING,
            limit = 10,
            excludeIds = emptyList()
        ).collectList().block()!!

        // Then: contentB1은 제외됨
        assertFalse(result.contains(contentB1Id))
    }

    // ==================== 언어 가중치 테스트 (Issue #107) ====================

    @Test
    @DisplayName("[Popular] 선호 언어와 일치하는 콘텐츠가 2.0x 가중치로 우선 추천된다")
    fun `should prioritize content matching preferred language with 2x weight in popular strategy`() {
        // Given: 동일한 인기도의 한국어/영어 콘텐츠 생성
        val testId = UUID.randomUUID().toString().substring(0, 8)
        val (creator, _) = createUserWithProfile(
            userRepository = userRepository,
            userProfileRepository = userProfileRepository,
            email = "creator-lang-$testId@example.com",
            nickname = "c-lang-$testId"
        )

        // 한국어 콘텐츠: 10 likes
        val koreanContent = createContent(
            contentRepository = contentRepository,
            creatorId = creator.id!!,
            title = "Korean Content",
            category = Category.PROGRAMMING,
            language = "ko",
            contentInteractionRepository = contentInteractionRepository
        )
        repeat(10) {
            contentInteractionRepository.incrementLikeCount(koreanContent.id!!).block()
        }

        // 영어 콘텐츠: 10 likes (동일한 인기도)
        val englishContent = createContent(
            contentRepository = contentRepository,
            creatorId = creator.id!!,
            title = "English Content",
            category = Category.PROGRAMMING,
            language = "en",
            contentInteractionRepository = contentInteractionRepository
        )
        repeat(10) {
            contentInteractionRepository.incrementLikeCount(englishContent.id!!).block()
        }

        // When: 한국어 사용자가 인기 콘텐츠 조회
        val koreanUserResult = feedRepository.findPopularContentIds(
            userId = userAId,
            limit = 10,
            excludeIds = emptyList(),
            category = null,
            preferredLanguage = "ko"
        ).collectList().block()!!

        // Then: 한국어 콘텐츠가 먼저 나와야 함 (100 * 2.0 = 200 vs 100 * 0.5 = 50)
        val koreanContentIndex = koreanUserResult.indexOf(koreanContent.id!!)
        val englishContentIndex = koreanUserResult.indexOf(englishContent.id!!)
        assertTrue(koreanContentIndex < englishContentIndex,
            "Korean content should come before English content for Korean user (index: $koreanContentIndex < $englishContentIndex)")

        // When: 영어 사용자가 인기 콘텐츠 조회
        val englishUserResult = feedRepository.findPopularContentIds(
            userId = userAId,
            limit = 10,
            excludeIds = emptyList(),
            category = null,
            preferredLanguage = "en"
        ).collectList().block()!!

        // Then: 영어 콘텐츠가 먼저 나와야 함 (100 * 2.0 = 200 vs 100 * 0.5 = 50)
        val koreanContentIndex2 = englishUserResult.indexOf(koreanContent.id!!)
        val englishContentIndex2 = englishUserResult.indexOf(englishContent.id!!)
        assertTrue(englishContentIndex2 < koreanContentIndex2,
            "English content should come before Korean content for English user (index: $englishContentIndex2 < $koreanContentIndex2)")
    }

    @Test
    @DisplayName("[New] 선호 언어와 일치하는 콘텐츠가 2.0x 가중치로 우선 추천된다")
    fun `should prioritize content matching preferred language with 2x weight in new strategy`() {
        // Given: 동일한 시간에 생성된 한국어/영어 콘텐츠
        val testId = UUID.randomUUID().toString().substring(0, 8)
        val (creator, _) = createUserWithProfile(
            userRepository = userRepository,
            userProfileRepository = userProfileRepository,
            email = "creator-new-$testId@example.com",
            nickname = "c-new-$testId"
        )

        // 한국어 신규 콘텐츠
        val koreanContent = createContent(
            contentRepository = contentRepository,
            creatorId = creator.id!!,
            title = "New Korean Content",
            category = Category.PROGRAMMING,
            language = "ko",
            contentInteractionRepository = contentInteractionRepository
        )

        // 영어 신규 콘텐츠 (동일 시간대)
        val englishContent = createContent(
            contentRepository = contentRepository,
            creatorId = creator.id!!,
            title = "New English Content",
            category = Category.PROGRAMMING,
            language = "en",
            contentInteractionRepository = contentInteractionRepository
        )

        // When: 한국어 사용자가 신규 콘텐츠 조회
        val koreanUserResult = feedRepository.findNewContentIds(
            userId = userAId,
            limit = 10,
            excludeIds = emptyList(),
            category = null,
            preferredLanguage = "ko"
        ).collectList().block()!!

        // Then: 한국어 콘텐츠가 먼저 나와야 함
        val koreanContentIndex = koreanUserResult.indexOf(koreanContent.id!!)
        val englishContentIndex = koreanUserResult.indexOf(englishContent.id!!)
        assertTrue(koreanContentIndex < englishContentIndex,
            "Korean content should come before English content for Korean user in new strategy")

        // When: 영어 사용자가 신규 콘텐츠 조회
        val englishUserResult = feedRepository.findNewContentIds(
            userId = userAId,
            limit = 10,
            excludeIds = emptyList(),
            category = null,
            preferredLanguage = "en"
        ).collectList().block()!!

        // Then: 영어 콘텐츠가 먼저 나와야 함
        val koreanContentIndex2 = englishUserResult.indexOf(koreanContent.id!!)
        val englishContentIndex2 = englishUserResult.indexOf(englishContent.id!!)
        assertTrue(englishContentIndex2 < koreanContentIndex2,
            "English content should come before Korean content for English user in new strategy")
    }

    @Test
    @DisplayName("[Random] 랜덤 전략에서도 언어 가중치가 적용되어 선호 언어가 더 자주 나온다")
    fun `should apply language weight in random strategy`() {
        // Given: 다수의 한국어/영어 콘텐츠 생성
        val testId = UUID.randomUUID().toString().substring(0, 8)
        val (creator, _) = createUserWithProfile(
            userRepository = userRepository,
            userProfileRepository = userProfileRepository,
            email = "creator-rand-$testId@example.com",
            nickname = "c-rand-$testId"
        )

        val koreanContents = mutableListOf<UUID>()
        val englishContents = mutableListOf<UUID>()

        repeat(10) { i ->
            // 한국어 콘텐츠
            val korean = createContent(
                contentRepository = contentRepository,
                creatorId = creator.id!!,
                title = "Korean Random $i",
                category = Category.ART,
                language = "ko",
                contentInteractionRepository = contentInteractionRepository
            )
            koreanContents.add(korean.id!!)

            // 영어 콘텐츠
            val english = createContent(
                contentRepository = contentRepository,
                creatorId = creator.id!!,
                title = "English Random $i",
                category = Category.ART,
                language = "en",
                contentInteractionRepository = contentInteractionRepository
            )
            englishContents.add(english.id!!)
        }

        // When: 한국어 사용자가 랜덤 콘텐츠 조회 (100번 반복하여 통계적 검증)
        var koreanCount = 0
        var englishCount = 0

        repeat(50) {
            val result = feedRepository.findRandomContentIds(
                userId = userAId,
                limit = 10,
                excludeIds = emptyList(),
                category = Category.ART,
                preferredLanguage = "ko"
            ).collectList().block()!!

            // 상위 5개 중 한국어/영어 개수 카운트
            result.take(5).forEach { contentId ->
                when {
                    koreanContents.contains(contentId) -> koreanCount++
                    englishContents.contains(contentId) -> englishCount++
                }
            }
        }

        // Then: 한국어 콘텐츠가 영어 콘텐츠보다 유의미하게 더 많이 나와야 함
        // 언어 가중치가 없다면 50:50, 있다면 대략 80:20 또는 그 이상
        assertTrue(koreanCount > englishCount,
            "Korean content should appear more frequently than English content for Korean user (Korean: $koreanCount, English: $englishCount)")

        // 최소 1.5배 이상 차이 나야 함 (통계적 유의성)
        assertTrue(koreanCount > englishCount * 1.5,
            "Korean content should appear at least 1.5x more than English content (Korean: $koreanCount, English: $englishCount)")
    }

    @Test
    @DisplayName("[Category Filter] 카테고리별 조회 시에도 언어 가중치가 적용된다")
    fun `should apply language weight with category filter`() {
        // Given: 동일 카테고리의 한국어/영어 콘텐츠
        val testId = UUID.randomUUID().toString().substring(0, 8)
        val (creator, _) = createUserWithProfile(
            userRepository = userRepository,
            userProfileRepository = userProfileRepository,
            email = "creator-cat-$testId@example.com",
            nickname = "c-cat-$testId"
        )

        // 한국어 PROGRAMMING 콘텐츠: 50 likes
        val koreanContent = createContent(
            contentRepository = contentRepository,
            creatorId = creator.id!!,
            title = "Korean Programming",
            category = Category.PROGRAMMING,
            language = "ko",
            contentInteractionRepository = contentInteractionRepository
        )
        repeat(50) {
            contentInteractionRepository.incrementLikeCount(koreanContent.id!!).block()
        }

        // 영어 PROGRAMMING 콘텐츠: 50 likes
        val englishContent = createContent(
            contentRepository = contentRepository,
            creatorId = creator.id!!,
            title = "English Programming",
            category = Category.PROGRAMMING,
            language = "en",
            contentInteractionRepository = contentInteractionRepository
        )
        repeat(50) {
            contentInteractionRepository.incrementLikeCount(englishContent.id!!).block()
        }

        // When: PROGRAMMING 카테고리에서 한국어 사용자가 조회
        val result = feedRepository.findPopularContentIds(
            userId = userAId,
            limit = 10,
            excludeIds = emptyList(),
            category = Category.PROGRAMMING,
            preferredLanguage = "ko"
        ).collectList().block()!!

        // Then: 한국어 콘텐츠가 먼저 나와야 함
        val koreanContentIndex = result.indexOf(koreanContent.id!!)
        val englishContentIndex = result.indexOf(englishContent.id!!)
        assertTrue(koreanContentIndex < englishContentIndex,
            "Korean content should come first in category filter with language weight")
    }

    // ==================== 헬퍼 메서드 ====================

    private fun blockUser(blockerId: UUID, blockedId: UUID) {
        Mono.from(
            dslContext.insertInto(USER_BLOCKS)
                .set(USER_BLOCKS.BLOCKER_ID, blockerId.toString())
                .set(USER_BLOCKS.BLOCKED_ID, blockedId.toString())
        ).block()
    }

    private fun blockContent(userId: UUID, contentId: UUID) {
        Mono.from(
            dslContext.insertInto(CONTENT_BLOCKS)
                .set(CONTENT_BLOCKS.USER_ID, userId.toString())
                .set(CONTENT_BLOCKS.CONTENT_ID, contentId.toString())
        ).block()
    }

    private fun follow(followerId: UUID, followingId: UUID) {
        Mono.from(
            dslContext.insertInto(FOLLOWS)
                .set(FOLLOWS.FOLLOWER_ID, followerId.toString())
                .set(FOLLOWS.FOLLOWING_ID, followingId.toString())
        ).block()
    }

}
