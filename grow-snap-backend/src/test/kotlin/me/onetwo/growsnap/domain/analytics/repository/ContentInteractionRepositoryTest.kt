package me.onetwo.growsnap.domain.analytics.repository

import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.jooq.generated.tables.references.COMMENTS
import me.onetwo.growsnap.jooq.generated.tables.references.USER_COMMENT_LIKES
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENTS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_INTERACTIONS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_METADATA
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_PHOTOS
import me.onetwo.growsnap.jooq.generated.tables.references.USER_CONTENT_INTERACTIONS
import me.onetwo.growsnap.jooq.generated.tables.references.USER_LIKES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_PROFILES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_SAVES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY
import me.onetwo.growsnap.jooq.generated.tables.references.USERS
import org.jooq.DSLContext
import org.jooq.JSON
import reactor.core.publisher.Mono
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.UUID

/**
 * ContentInteractionRepository 통합 테스트
 *
 * 실제 데이터베이스(H2)를 사용하여 인터랙션 카운터 증가 기능을 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("콘텐츠 인터랙션 Repository 통합 테스트")
class ContentInteractionRepositoryTest {

    @Autowired
    private lateinit var contentInteractionRepository: ContentInteractionRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    private lateinit var testUser: User
    private lateinit var testContentId: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트 데이터 준비

        // 사용자 생성
        testUser = userRepository.save(
            User(
                email = "creator@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "creator-123",
                role = UserRole.USER
            )
        ).block()!!

        // 콘텐츠 생성
        testContentId = UUID.randomUUID()
        insertContent(testContentId, testUser.id!!, "Test Video")
    }

    @AfterEach
    fun tearDown() {
        Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)).block()
        Mono.from(dslContext.deleteFrom(USER_CONTENT_INTERACTIONS)).block()
        Mono.from(dslContext.deleteFrom(USER_VIEW_HISTORY)).block()
        Mono.from(dslContext.deleteFrom(USER_SAVES)).block()
        Mono.from(dslContext.deleteFrom(USER_LIKES)).block()
        Mono.from(dslContext.deleteFrom(USER_COMMENT_LIKES)).block()
        Mono.from(dslContext.deleteFrom(COMMENTS)).block()
        Mono.from(dslContext.deleteFrom(CONTENT_PHOTOS)).block()
        Mono.from(dslContext.deleteFrom(CONTENTS)).block()
        Mono.from(dslContext.deleteFrom(USER_PROFILES)).block()
        Mono.from(dslContext.deleteFrom(USERS)).block()
    }

    @Nested
    @DisplayName("create - ContentInteraction 생성")
    inner class Create {

        @Test
        @DisplayName("새로운 ContentInteraction을 생성한다")
        fun create_CreatesNewContentInteraction() {
            // Given: 새로운 콘텐츠 ID
            val newContentId = UUID.randomUUID()

            // 콘텐츠 먼저 생성 (FK 제약조건)
            val now = LocalDateTime.now()
            dslContext.insertInto(CONTENTS)
                .set(CONTENTS.ID, newContentId.toString())
                .set(CONTENTS.CREATOR_ID, testUser.id.toString())
                .set(CONTENTS.CONTENT_TYPE, ContentType.VIDEO.name)
                .set(CONTENTS.URL, "https://example.com/test.mp4")
                .set(CONTENTS.THUMBNAIL_URL, "https://example.com/thumb.jpg")
                .set(CONTENTS.DURATION, 60)
                .set(CONTENTS.WIDTH, 1920)
                .set(CONTENTS.HEIGHT, 1080)
                .set(CONTENTS.STATUS, ContentStatus.PUBLISHED.name)
                .set(CONTENTS.CREATED_AT, now)
                .set(CONTENTS.CREATED_BY, testUser.id.toString())
                .set(CONTENTS.UPDATED_AT, now)
                .set(CONTENTS.UPDATED_BY, testUser.id.toString())
                .execute()

            val contentInteraction = me.onetwo.growsnap.domain.content.model.ContentInteraction(
                contentId = newContentId,
                likeCount = 0,
                commentCount = 0,
                saveCount = 0,
                shareCount = 0,
                viewCount = 0,
                createdAt = now,
                createdBy = testUser.id.toString(),
                updatedAt = now,
                updatedBy = testUser.id.toString()
            )

            // When: ContentInteraction 생성
            contentInteractionRepository.create(contentInteraction).block()

            // Then: 데이터베이스에 저장되었는지 확인
            val savedRecord = dslContext.selectFrom(CONTENT_INTERACTIONS)
                .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(newContentId.toString()))
                .fetchOne()

            assertEquals(newContentId.toString(), savedRecord?.contentId)
            assertEquals(0, savedRecord?.likeCount)
            assertEquals(0, savedRecord?.commentCount)
            assertEquals(0, savedRecord?.saveCount)
            assertEquals(0, savedRecord?.shareCount)
            assertEquals(0, savedRecord?.viewCount)
            assertEquals(testUser.id.toString(), savedRecord?.createdBy)
            assertEquals(testUser.id.toString(), savedRecord?.updatedBy)
        }

        @Test
        @DisplayName("nullable createdBy/updatedBy를 처리한다")
        fun create_HandlesNullableFields() {
            // Given: 새로운 콘텐츠 ID와 nullable 필드가 있는 ContentInteraction
            val newContentId = UUID.randomUUID()

            // 콘텐츠 먼저 생성
            val now = LocalDateTime.now()
            dslContext.insertInto(CONTENTS)
                .set(CONTENTS.ID, newContentId.toString())
                .set(CONTENTS.CREATOR_ID, testUser.id.toString())
                .set(CONTENTS.CONTENT_TYPE, ContentType.VIDEO.name)
                .set(CONTENTS.URL, "https://example.com/test.mp4")
                .set(CONTENTS.THUMBNAIL_URL, "https://example.com/thumb.jpg")
                .set(CONTENTS.DURATION, 60)
                .set(CONTENTS.WIDTH, 1920)
                .set(CONTENTS.HEIGHT, 1080)
                .set(CONTENTS.STATUS, ContentStatus.PUBLISHED.name)
                .set(CONTENTS.CREATED_AT, now)
                .set(CONTENTS.UPDATED_AT, now)
                .execute()

            val contentInteraction = me.onetwo.growsnap.domain.content.model.ContentInteraction(
                contentId = newContentId,
                likeCount = 0,
                commentCount = 0,
                saveCount = 0,
                shareCount = 0,
                viewCount = 0,
                createdAt = now,
                createdBy = null,  // nullable
                updatedAt = now,
                updatedBy = null   // nullable
            )

            // When: ContentInteraction 생성
            contentInteractionRepository.create(contentInteraction).block()

            // Then: 데이터베이스에 저장되었는지 확인
            val savedRecord = dslContext.selectFrom(CONTENT_INTERACTIONS)
                .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(newContentId.toString()))
                .fetchOne()

            assertEquals(newContentId.toString(), savedRecord?.contentId)
            assertEquals(null, savedRecord?.createdBy)
            assertEquals(null, savedRecord?.updatedBy)
        }
    }

    @Nested
    @DisplayName("incrementViewCount - 조회수 증가")
    inner class IncrementViewCount {

        @Test
        @DisplayName("조회수를 1 증가시킨다")
        fun incrementViewCount_IncreasesCountByOne() {
            // Given: 초기 조회수 확인
            val initialCount = getViewCount(testContentId)

            // When: 조회수 증가
            contentInteractionRepository.incrementViewCount(testContentId).block()

            // Then: 1 증가 확인
            val updatedCount = getViewCount(testContentId)
            assertEquals(initialCount + 1, updatedCount)
        }

        @Test
        @DisplayName("여러 번 증가 시, 누적된다")
        fun incrementViewCount_MultipleTimes_Accumulates() {
            // Given: 초기 조회수 확인
            val initialCount = getViewCount(testContentId)

            // When: 3번 증가
            contentInteractionRepository.incrementViewCount(testContentId).block()
            contentInteractionRepository.incrementViewCount(testContentId).block()
            contentInteractionRepository.incrementViewCount(testContentId).block()

            // Then: 3 증가 확인
            val updatedCount = getViewCount(testContentId)
            assertEquals(initialCount + 3, updatedCount)
        }

        @Test
        @DisplayName("삭제된 콘텐츠는 업데이트되지 않는다")
        fun incrementViewCount_DeletedContent_DoesNotUpdate() {
            // Given: 콘텐츠 삭제 (Soft Delete)
            dslContext.update(CONTENT_INTERACTIONS)
                .set(CONTENT_INTERACTIONS.DELETED_AT, LocalDateTime.now())
                .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(testContentId.toString()))
                .execute()

            val initialCount = getViewCount(testContentId)

            // When: 조회수 증가 시도
            contentInteractionRepository.incrementViewCount(testContentId).block()

            // Then: 변경 없음
            val updatedCount = getViewCount(testContentId)
            assertEquals(initialCount, updatedCount)
        }
    }

    @Nested
    @DisplayName("incrementLikeCount - 좋아요 수 증가")
    inner class IncrementLikeCount {

        @Test
        @DisplayName("좋아요 수를 1 증가시킨다")
        fun incrementLikeCount_IncreasesCountByOne() {
            // Given: 초기 좋아요 수 확인
            val initialCount = getLikeCount(testContentId)

            // When: 좋아요 수 증가
            contentInteractionRepository.incrementLikeCount(testContentId).block()

            // Then: 1 증가 확인
            val updatedCount = getLikeCount(testContentId)
            assertEquals(initialCount + 1, updatedCount)
        }

        @Test
        @DisplayName("여러 번 증가 시, 누적된다")
        fun incrementLikeCount_MultipleTimes_Accumulates() {
            // Given: 초기 좋아요 수 확인
            val initialCount = getLikeCount(testContentId)

            // When: 5번 증가
            repeat(5) {
                contentInteractionRepository.incrementLikeCount(testContentId).block()
            }

            // Then: 5 증가 확인
            val updatedCount = getLikeCount(testContentId)
            assertEquals(initialCount + 5, updatedCount)
        }
    }

    @Nested
    @DisplayName("incrementSaveCount - 저장 수 증가")
    inner class IncrementSaveCount {

        @Test
        @DisplayName("저장 수를 1 증가시킨다")
        fun incrementSaveCount_IncreasesCountByOne() {
            // Given: 초기 저장 수 확인
            val initialCount = getSaveCount(testContentId)

            // When: 저장 수 증가
            contentInteractionRepository.incrementSaveCount(testContentId).block()

            // Then: 1 증가 확인
            val updatedCount = getSaveCount(testContentId)
            assertEquals(initialCount + 1, updatedCount)
        }

        @Test
        @DisplayName("여러 번 증가 시, 누적된다")
        fun incrementSaveCount_MultipleTimes_Accumulates() {
            // Given: 초기 저장 수 확인
            val initialCount = getSaveCount(testContentId)

            // When: 3번 증가
            repeat(3) {
                contentInteractionRepository.incrementSaveCount(testContentId).block()
            }

            // Then: 3 증가 확인
            val updatedCount = getSaveCount(testContentId)
            assertEquals(initialCount + 3, updatedCount)
        }
    }

    @Nested
    @DisplayName("incrementShareCount - 공유 수 증가")
    inner class IncrementShareCount {

        @Test
        @DisplayName("공유 수를 1 증가시킨다")
        fun incrementShareCount_IncreasesCountByOne() {
            // Given: 초기 공유 수 확인
            val initialCount = getShareCount(testContentId)

            // When: 공유 수 증가
            contentInteractionRepository.incrementShareCount(testContentId).block()

            // Then: 1 증가 확인
            val updatedCount = getShareCount(testContentId)
            assertEquals(initialCount + 1, updatedCount)
        }

        @Test
        @DisplayName("여러 번 증가 시, 누적된다")
        fun incrementShareCount_MultipleTimes_Accumulates() {
            // Given: 초기 공유 수 확인
            val initialCount = getShareCount(testContentId)

            // When: 2번 증가
            repeat(2) {
                contentInteractionRepository.incrementShareCount(testContentId).block()
            }

            // Then: 2 증가 확인
            val updatedCount = getShareCount(testContentId)
            assertEquals(initialCount + 2, updatedCount)
        }
    }

    @Nested
    @DisplayName("incrementCommentCount - 댓글 수 증가")
    inner class IncrementCommentCount {

        @Test
        @DisplayName("댓글 수를 1 증가시킨다")
        fun incrementCommentCount_IncreasesCountByOne() {
            // Given: 초기 댓글 수 확인
            val initialCount = getCommentCount(testContentId)

            // When: 댓글 수 증가
            contentInteractionRepository.incrementCommentCount(testContentId).block()

            // Then: 1 증가 확인
            val updatedCount = getCommentCount(testContentId)
            assertEquals(initialCount + 1, updatedCount)
        }

        @Test
        @DisplayName("여러 번 증가 시, 누적된다")
        fun incrementCommentCount_MultipleTimes_Accumulates() {
            // Given: 초기 댓글 수 확인
            val initialCount = getCommentCount(testContentId)

            // When: 7번 증가
            repeat(7) {
                contentInteractionRepository.incrementCommentCount(testContentId).block()
            }

            // Then: 7 증가 확인
            val updatedCount = getCommentCount(testContentId)
            assertEquals(initialCount + 7, updatedCount)
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

        // Content_Interactions 테이블 (초기값 0)
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
     * 조회수 조회 헬퍼 메서드
     */
    private fun getViewCount(contentId: UUID): Int {
        return dslContext.select(CONTENT_INTERACTIONS.VIEW_COUNT)
            .from(CONTENT_INTERACTIONS)
            .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
            .fetchOne(CONTENT_INTERACTIONS.VIEW_COUNT) ?: 0
    }

    /**
     * 좋아요 수 조회 헬퍼 메서드
     */
    private fun getLikeCount(contentId: UUID): Int {
        return dslContext.select(CONTENT_INTERACTIONS.LIKE_COUNT)
            .from(CONTENT_INTERACTIONS)
            .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
            .fetchOne(CONTENT_INTERACTIONS.LIKE_COUNT) ?: 0
    }

    /**
     * 저장 수 조회 헬퍼 메서드
     */
    private fun getSaveCount(contentId: UUID): Int {
        return dslContext.select(CONTENT_INTERACTIONS.SAVE_COUNT)
            .from(CONTENT_INTERACTIONS)
            .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
            .fetchOne(CONTENT_INTERACTIONS.SAVE_COUNT) ?: 0
    }

    /**
     * 공유 수 조회 헬퍼 메서드
     */
    private fun getShareCount(contentId: UUID): Int {
        return dslContext.select(CONTENT_INTERACTIONS.SHARE_COUNT)
            .from(CONTENT_INTERACTIONS)
            .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
            .fetchOne(CONTENT_INTERACTIONS.SHARE_COUNT) ?: 0
    }

    /**
     * 댓글 수 조회 헬퍼 메서드
     */
    private fun getCommentCount(contentId: UUID): Int {
        return dslContext.select(CONTENT_INTERACTIONS.COMMENT_COUNT)
            .from(CONTENT_INTERACTIONS)
            .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
            .fetchOne(CONTENT_INTERACTIONS.COMMENT_COUNT) ?: 0
    }
}
