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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

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
    @DisplayName("차단한 사용자의 콘텐츠는 메인 피드에서 조회되지 않는다")
    fun `should not show blocked user content in main feed`() {
        // Given: User A가 User B를 차단
        Mono.from(dslContext.insertInto(USER_BLOCKS)
            .set(USER_BLOCKS.BLOCKER_ID, userAId.toString())
            .set(USER_BLOCKS.BLOCKED_ID, userBId.toString()))
            .block()

        // When: User A가 메인 피드 조회
        val result = feedRepository.findMainFeed(
            userId = userAId,
            cursor = null,
            limit = 20,
            excludeContentIds = emptyList()
        )

        // Then: User B의 콘텐츠가 포함되지 않음
        StepVerifier.create(result.collectList())
            .expectNextMatches { feeds ->
                // User B의 콘텐츠가 없어야 함
                feeds.none { it.contentId == contentB1Id } &&
                feeds.none { it.contentId == contentB2Id } &&
                // User C의 콘텐츠는 있어야 함
                feeds.any { it.contentId == contentC1Id }
            }
            .verifyComplete()
    }

    @Test
    @DisplayName("차단한 콘텐츠는 메인 피드에서 조회되지 않는다")
    fun `should not show blocked content in main feed`() {
        // Given: User A가 특정 콘텐츠를 차단
        Mono.from(dslContext.insertInto(CONTENT_BLOCKS)
            .set(CONTENT_BLOCKS.USER_ID, userAId.toString())
            .set(CONTENT_BLOCKS.CONTENT_ID, contentB1Id.toString()))
            .block()

        // When: User A가 메인 피드 조회
        val result = feedRepository.findMainFeed(
            userId = userAId,
            cursor = null,
            limit = 20,
            excludeContentIds = emptyList()
        )

        // Then: 차단한 콘텐츠가 포함되지 않음
        StepVerifier.create(result.collectList())
            .expectNextMatches { feeds ->
                // 차단한 콘텐츠는 없어야 함
                feeds.none { it.contentId == contentB1Id } &&
                // 차단하지 않은 User B의 다른 콘텐츠는 있어야 함
                feeds.any { it.contentId == contentB2Id } &&
                // User C의 콘텐츠는 있어야 함
                feeds.any { it.contentId == contentC1Id }
            }
            .verifyComplete()
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
    @DisplayName("차단하지 않은 콘텐츠는 메인 피드에서 정상적으로 조회된다")
    fun `should show non-blocked content in main feed normally`() {
        // Given: User A는 아무도 차단하지 않음

        // When: User A가 메인 피드 조회
        val result = feedRepository.findMainFeed(
            userId = userAId,
            cursor = null,
            limit = 20,
            excludeContentIds = emptyList()
        )

        // Then: 모든 콘텐츠가 정상적으로 조회됨
        StepVerifier.create(result.collectList())
            .expectNextMatches { feeds ->
                // 모든 사용자의 콘텐츠가 있어야 함
                feeds.any { it.contentId == contentB1Id } &&
                feeds.any { it.contentId == contentB2Id } &&
                feeds.any { it.contentId == contentC1Id }
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

    @Test
    @DisplayName("사용자와 콘텐츠를 모두 차단한 경우 메인 피드에서 모두 조회되지 않는다")
    fun `should not show any blocked user or content in main feed`() {
        // Given: User A가 User B를 차단
        Mono.from(dslContext.insertInto(USER_BLOCKS)
            .set(USER_BLOCKS.BLOCKER_ID, userAId.toString())
            .set(USER_BLOCKS.BLOCKED_ID, userBId.toString()))
            .block()

        // Given: User A가 User C의 특정 콘텐츠를 차단
        Mono.from(dslContext.insertInto(CONTENT_BLOCKS)
            .set(CONTENT_BLOCKS.USER_ID, userAId.toString())
            .set(CONTENT_BLOCKS.CONTENT_ID, contentC1Id.toString()))
            .block()

        // When: User A가 메인 피드 조회
        val result = feedRepository.findMainFeed(
            userId = userAId,
            cursor = null,
            limit = 20,
            excludeContentIds = emptyList()
        )

        // Then: 차단한 사용자와 콘텐츠 모두 포함되지 않음
        StepVerifier.create(result.collectList())
            .expectNextMatches { feeds ->
                // User B의 모든 콘텐츠가 없어야 함 (사용자 차단)
                feeds.none { it.contentId == contentB1Id } &&
                feeds.none { it.contentId == contentB2Id } &&
                // User C의 차단한 콘텐츠가 없어야 함 (콘텐츠 차단)
                feeds.none { it.contentId == contentC1Id }
            }
            .verifyComplete()
    }
}
