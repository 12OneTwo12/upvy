package me.onetwo.growsnap.domain.analytics.repository

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
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
import me.onetwo.growsnap.jooq.generated.tables.references.USER_CONTENT_INTERACTIONS
import org.jooq.DSLContext
import org.jooq.JSON
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * UserContentInteractionRepository 통합 테스트
 *
 * 실제 데이터베이스(H2)를 사용하여 사용자 인터랙션 관리 기능을 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("사용자 콘텐츠 인터랙션 Repository 통합 테스트")
class UserContentInteractionRepositoryTest {

    @Autowired
    private lateinit var userContentInteractionRepository: UserContentInteractionRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    private lateinit var testUser1: User
    private lateinit var testUser2: User
    private lateinit var testUser3: User
    private lateinit var testContent1Id: UUID
    private lateinit var testContent2Id: UUID
    private lateinit var testContent3Id: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트 데이터 준비

        // 사용자 3명 생성
        testUser1 = userRepository.save(
            User(
                email = "user1@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "user1-123",
                role = UserRole.USER
            )
        )

        testUser2 = userRepository.save(
            User(
                email = "user2@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "user2-123",
                role = UserRole.USER
            )
        )

        testUser3 = userRepository.save(
            User(
                email = "user3@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "user3-123",
                role = UserRole.USER
            )
        )

        // 콘텐츠 3개 생성
        testContent1Id = UUID.randomUUID()
        testContent2Id = UUID.randomUUID()
        testContent3Id = UUID.randomUUID()

        insertContent(testContent1Id, testUser1.id!!, "Content 1")
        insertContent(testContent2Id, testUser1.id!!, "Content 2")
        insertContent(testContent3Id, testUser1.id!!, "Content 3")
    }

    @Nested
    @DisplayName("save - 인터랙션 저장")
    inner class Save {

        @Test
        @DisplayName("인터랙션을 저장한다")
        fun save_CreatesInteraction() {
            // When: 인터랙션 저장
            userContentInteractionRepository.save(
                testUser1.id!!,
                testContent1Id,
                InteractionType.LIKE
            ).block()

            // Then: 저장 확인
            val count = countInteractions(testUser1.id!!, testContent1Id, InteractionType.LIKE)
            assertEquals(1, count)
        }

        @Test
        @DisplayName("중복 인터랙션은 무시된다")
        fun save_DuplicateInteraction_IsIgnored() {
            // Given: 이미 저장된 인터랙션
            userContentInteractionRepository.save(
                testUser1.id!!,
                testContent1Id,
                InteractionType.LIKE
            ).block()

            // When: 동일한 인터랙션 다시 저장
            userContentInteractionRepository.save(
                testUser1.id!!,
                testContent1Id,
                InteractionType.LIKE
            ).block()

            // Then: 여전히 1개만 존재
            val count = countInteractions(testUser1.id!!, testContent1Id, InteractionType.LIKE)
            assertEquals(1, count)
        }

        @Test
        @DisplayName("다른 인터랙션 타입은 별도로 저장된다")
        fun save_DifferentTypes_SavesSeparately() {
            // When: 같은 사용자-콘텐츠에 다른 타입의 인터랙션 저장
            userContentInteractionRepository.save(
                testUser1.id!!,
                testContent1Id,
                InteractionType.LIKE
            ).block()

            userContentInteractionRepository.save(
                testUser1.id!!,
                testContent1Id,
                InteractionType.SAVE
            ).block()

            // Then: 각각 저장됨
            val likeCount = countInteractions(testUser1.id!!, testContent1Id, InteractionType.LIKE)
            val saveCount = countInteractions(testUser1.id!!, testContent1Id, InteractionType.SAVE)
            assertEquals(1, likeCount)
            assertEquals(1, saveCount)
        }
    }

    @Nested
    @DisplayName("findContentIdsByUser - 사용자가 인터랙션한 콘텐츠 조회")
    inner class FindContentIdsByUser {

        @Test
        @DisplayName("특정 타입의 인터랙션한 콘텐츠를 반환한다")
        fun findContentIdsByUser_WithType_ReturnsMatchingContents() {
            // Given: user1이 content1, content2에 LIKE
            saveInteraction(testUser1.id!!, testContent1Id, InteractionType.LIKE)
            saveInteraction(testUser1.id!!, testContent2Id, InteractionType.LIKE)
            saveInteraction(testUser1.id!!, testContent3Id, InteractionType.SAVE)

            // When: LIKE 타입 조회
            val result = userContentInteractionRepository.findContentIdsByUser(
                testUser1.id!!,
                InteractionType.LIKE,
                10
            ).collectList().block()!!

            // Then: content1, content2만 반환
            assertEquals(2, result.size)
            assertTrue(result.contains(testContent1Id))
            assertTrue(result.contains(testContent2Id))
        }

        @Test
        @DisplayName("타입이 null이면 모든 타입의 콘텐츠를 반환한다")
        fun findContentIdsByUser_WithNullType_ReturnsAllContents() {
            // Given: user1이 여러 타입으로 인터랙션
            saveInteraction(testUser1.id!!, testContent1Id, InteractionType.LIKE)
            saveInteraction(testUser1.id!!, testContent2Id, InteractionType.SAVE)
            saveInteraction(testUser1.id!!, testContent3Id, InteractionType.SHARE)

            // When: null 타입으로 조회
            val result = userContentInteractionRepository.findContentIdsByUser(
                testUser1.id!!,
                null,
                10
            ).collectList().block()!!

            // Then: 모든 콘텐츠 반환
            assertEquals(3, result.size)
        }

        @Test
        @DisplayName("limit만큼만 반환한다")
        fun findContentIdsByUser_WithLimit_ReturnsLimitedResults() {
            // Given: user1이 3개 콘텐츠에 인터랙션
            saveInteraction(testUser1.id!!, testContent1Id, InteractionType.LIKE)
            saveInteraction(testUser1.id!!, testContent2Id, InteractionType.LIKE)
            saveInteraction(testUser1.id!!, testContent3Id, InteractionType.LIKE)

            // When: limit=2
            val result = userContentInteractionRepository.findContentIdsByUser(
                testUser1.id!!,
                InteractionType.LIKE,
                2
            ).collectList().block()!!

            // Then: 2개만 반환
            assertEquals(2, result.size)
        }

        @Test
        @DisplayName("인터랙션이 없으면 빈 목록을 반환한다")
        fun findContentIdsByUser_NoInteractions_ReturnsEmptyList() {
            // When: 인터랙션 없는 사용자로 조회
            val result = userContentInteractionRepository.findContentIdsByUser(
                testUser2.id!!,
                InteractionType.LIKE,
                10
            ).collectList().block()!!

            // Then: 빈 목록
            assertEquals(0, result.size)
        }
    }

    @Nested
    @DisplayName("findUsersByContent - 콘텐츠에 인터랙션한 사용자 조회")
    inner class FindUsersByContent {

        @Test
        @DisplayName("특정 타입의 인터랙션한 사용자를 반환한다")
        fun findUsersByContent_WithType_ReturnsMatchingUsers() {
            // Given: content1에 user1, user2가 LIKE
            saveInteraction(testUser1.id!!, testContent1Id, InteractionType.LIKE)
            saveInteraction(testUser2.id!!, testContent1Id, InteractionType.LIKE)
            saveInteraction(testUser3.id!!, testContent1Id, InteractionType.SAVE)

            // When: LIKE 타입 조회
            val result = userContentInteractionRepository.findUsersByContent(
                testContent1Id,
                InteractionType.LIKE,
                10
            ).collectList().block()!!

            // Then: user1, user2만 반환
            assertEquals(2, result.size)
            assertTrue(result.contains(testUser1.id!!))
            assertTrue(result.contains(testUser2.id!!))
        }

        @Test
        @DisplayName("타입이 null이면 모든 타입의 사용자를 반환한다")
        fun findUsersByContent_WithNullType_ReturnsAllUsers() {
            // Given: content1에 여러 타입으로 인터랙션
            saveInteraction(testUser1.id!!, testContent1Id, InteractionType.LIKE)
            saveInteraction(testUser2.id!!, testContent1Id, InteractionType.SAVE)
            saveInteraction(testUser3.id!!, testContent1Id, InteractionType.SHARE)

            // When: null 타입으로 조회
            val result = userContentInteractionRepository.findUsersByContent(
                testContent1Id,
                null,
                10
            ).collectList().block()!!

            // Then: 모든 사용자 반환
            assertEquals(3, result.size)
        }

        @Test
        @DisplayName("limit만큼만 반환한다")
        fun findUsersByContent_WithLimit_ReturnsLimitedResults() {
            // Given: content1에 3명의 사용자가 인터랙션
            saveInteraction(testUser1.id!!, testContent1Id, InteractionType.LIKE)
            saveInteraction(testUser2.id!!, testContent1Id, InteractionType.LIKE)
            saveInteraction(testUser3.id!!, testContent1Id, InteractionType.LIKE)

            // When: limit=2
            val result = userContentInteractionRepository.findUsersByContent(
                testContent1Id,
                InteractionType.LIKE,
                2
            ).collectList().block()!!

            // Then: 2개만 반환
            assertEquals(2, result.size)
        }
    }

    @Nested
    @DisplayName("findAllInteractionsByUser - 사용자의 모든 인터랙션 조회")
    inner class FindAllInteractionsByUser {

        @Test
        @DisplayName("사용자의 모든 인터랙션을 반환한다")
        fun findAllInteractionsByUser_ReturnsAllInteractions() {
            // Given: user1이 여러 콘텐츠에 인터랙션
            saveInteraction(testUser1.id!!, testContent1Id, InteractionType.LIKE)
            saveInteraction(testUser1.id!!, testContent2Id, InteractionType.SAVE)
            saveInteraction(testUser1.id!!, testContent3Id, InteractionType.SHARE)

            // When: 모든 인터랙션 조회
            val result = userContentInteractionRepository.findAllInteractionsByUser(
                testUser1.id!!,
                10
            ).collectList().block()!!

            // Then: 3개 인터랙션 반환
            assertEquals(3, result.size)

            // contentId와 interactionType 매핑 확인
            val contentIds = result.map { it.contentId }
            val types = result.map { it.interactionType }

            assertTrue(contentIds.contains(testContent1Id))
            assertTrue(contentIds.contains(testContent2Id))
            assertTrue(contentIds.contains(testContent3Id))
            assertTrue(types.contains(InteractionType.LIKE))
            assertTrue(types.contains(InteractionType.SAVE))
            assertTrue(types.contains(InteractionType.SHARE))
        }

        @Test
        @DisplayName("같은 콘텐츠에 여러 타입의 인터랙션이 있으면 모두 반환한다")
        fun findAllInteractionsByUser_MultipleTypesPerContent_ReturnsAll() {
            // Given: user1이 content1에 LIKE, SAVE
            saveInteraction(testUser1.id!!, testContent1Id, InteractionType.LIKE)
            saveInteraction(testUser1.id!!, testContent1Id, InteractionType.SAVE)

            // When: 모든 인터랙션 조회
            val result = userContentInteractionRepository.findAllInteractionsByUser(
                testUser1.id!!,
                10
            ).collectList().block()!!

            // Then: 2개 반환
            assertEquals(2, result.size)
        }

        @Test
        @DisplayName("limit만큼만 반환한다")
        fun findAllInteractionsByUser_WithLimit_ReturnsLimitedResults() {
            // Given: user1이 5개 인터랙션
            saveInteraction(testUser1.id!!, testContent1Id, InteractionType.LIKE)
            saveInteraction(testUser1.id!!, testContent1Id, InteractionType.SAVE)
            saveInteraction(testUser1.id!!, testContent2Id, InteractionType.LIKE)
            saveInteraction(testUser1.id!!, testContent2Id, InteractionType.SHARE)
            saveInteraction(testUser1.id!!, testContent3Id, InteractionType.LIKE)

            // When: limit=3
            val result = userContentInteractionRepository.findAllInteractionsByUser(
                testUser1.id!!,
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
        val now = LocalDateTime.now()

        // Contents 테이블
        dslContext.insertInto(CONTENTS)
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
            .set(CONTENTS.UPDATED_BY, creatorId.toString())
            .execute()

        // Content_Metadata 테이블
        dslContext.insertInto(CONTENT_METADATA)
            .set(CONTENT_METADATA.CONTENT_ID, contentId.toString())
            .set(CONTENT_METADATA.TITLE, title)
            .set(CONTENT_METADATA.DESCRIPTION, "Test Description")
            .set(CONTENT_METADATA.CATEGORY, Category.PROGRAMMING.name)
            .set(CONTENT_METADATA.TAGS, JSON.valueOf("[\"test\"]"))
            .set(CONTENT_METADATA.LANGUAGE, "ko")
            .set(CONTENT_METADATA.CREATED_AT, now)
            .set(CONTENT_METADATA.CREATED_BY, creatorId.toString())
            .set(CONTENT_METADATA.UPDATED_AT, now)
            .set(CONTENT_METADATA.UPDATED_BY, creatorId.toString())
            .execute()

        // Content_Interactions 테이블
        dslContext.insertInto(CONTENT_INTERACTIONS)
            .set(CONTENT_INTERACTIONS.CONTENT_ID, contentId.toString())
            .set(CONTENT_INTERACTIONS.VIEW_COUNT, 0)
            .set(CONTENT_INTERACTIONS.LIKE_COUNT, 0)
            .set(CONTENT_INTERACTIONS.COMMENT_COUNT, 0)
            .set(CONTENT_INTERACTIONS.SAVE_COUNT, 0)
            .set(CONTENT_INTERACTIONS.SHARE_COUNT, 0)
            .set(CONTENT_INTERACTIONS.CREATED_AT, now)
            .set(CONTENT_INTERACTIONS.CREATED_BY, creatorId.toString())
            .set(CONTENT_INTERACTIONS.UPDATED_AT, now)
            .set(CONTENT_INTERACTIONS.UPDATED_BY, creatorId.toString())
            .execute()
    }

    /**
     * 인터랙션 저장 헬퍼 메서드
     */
    private fun saveInteraction(userId: UUID, contentId: UUID, type: InteractionType) {
        userContentInteractionRepository.save(userId, contentId, type).block()
    }

    /**
     * 인터랙션 개수 조회 헬퍼 메서드
     */
    private fun countInteractions(userId: UUID, contentId: UUID, type: InteractionType): Int {
        return dslContext.selectCount()
            .from(USER_CONTENT_INTERACTIONS)
            .where(USER_CONTENT_INTERACTIONS.USER_ID.eq(userId.toString()))
            .and(USER_CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
            .and(USER_CONTENT_INTERACTIONS.INTERACTION_TYPE.eq(type.name))
            .and(USER_CONTENT_INTERACTIONS.DELETED_AT.isNull)
            .fetchOne(0, Int::class.java) ?: 0
    }
}
