package me.onetwo.growsnap.domain.interaction.repository

import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.interaction.model.Comment
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.jooq.generated.tables.UserCommentLikes.Companion.USER_COMMENT_LIKES
import me.onetwo.growsnap.jooq.generated.tables.Comments.Companion.COMMENTS
import me.onetwo.growsnap.jooq.generated.tables.ContentInteractions.Companion.CONTENT_INTERACTIONS
import me.onetwo.growsnap.jooq.generated.tables.ContentMetadata.Companion.CONTENT_METADATA
import me.onetwo.growsnap.jooq.generated.tables.ContentPhotos.Companion.CONTENT_PHOTOS
import me.onetwo.growsnap.jooq.generated.tables.Contents.Companion.CONTENTS
import me.onetwo.growsnap.jooq.generated.tables.UserProfiles.Companion.USER_PROFILES
import me.onetwo.growsnap.jooq.generated.tables.Users.Companion.USERS
import org.jooq.DSLContext
import org.jooq.JSON
import org.jooq.exception.DataAccessException
import reactor.core.publisher.Mono
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

/**
 * CommentLikeRepository 통합 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CommentLikeRepository 통합 테스트")
class CommentLikeRepositoryTest {

    @Autowired
    private lateinit var commentLikeRepository: CommentLikeRepository

    @Autowired
    private lateinit var commentRepository: CommentRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    private lateinit var testUserId: UUID
    private lateinit var testCommentId: UUID
    private lateinit var testContentId: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트 데이터 준비
        val user = userRepository.save(
            me.onetwo.growsnap.domain.user.model.User(
                email = "testuser@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "test-provider-id",
                role = UserRole.USER
            )
        ).block()!!
        testUserId = user.id!!

        // 콘텐츠 생성
        testContentId = UUID.randomUUID()
        insertContent(testContentId, testUserId, "Test Content")

        // 댓글 생성
        val comment = commentRepository.save(
            Comment(
                contentId = testContentId,
                userId = testUserId,
                content = "Test comment"
            )
        ).block()!!
        testCommentId = comment.id!!
    }

    @AfterEach
    fun tearDown() {
        Mono.from(dslContext.deleteFrom(USER_COMMENT_LIKES)).block()
        Mono.from(dslContext.deleteFrom(COMMENTS)).block()
        Mono.from(dslContext.deleteFrom(CONTENT_PHOTOS)).block()
        Mono.from(dslContext.deleteFrom(CONTENTS)).block()
        Mono.from(dslContext.deleteFrom(USER_PROFILES)).block()
        Mono.from(dslContext.deleteFrom(USERS)).block()
    }

    @Nested
    @DisplayName("save - 댓글 좋아요 생성")
    inner class Save {

        @Test
        @DisplayName("댓글 좋아요를 생성하면, user_comment_likes 테이블에 레코드가 저장된다")
        fun save_CreatesCommentLike() {
            // Given: 준비된 사용자와 댓글

            // When: 댓글 좋아요 생성
            val commentLike = commentLikeRepository.save(testUserId, testCommentId).block()!!

            // Then: 생성된 댓글 좋아요 검증
            assertEquals(testUserId, commentLike.userId)
            assertEquals(testCommentId, commentLike.commentId)
            assertEquals(testUserId.toString(), commentLike.createdBy)
            assertEquals(testUserId.toString(), commentLike.updatedBy)
        }

        @Test
        @DisplayName("이미 댓글 좋아요가 존재하면, 중복 생성 시 예외가 발생한다")
        fun save_WhenAlreadyExists_ThrowsException() {
            // Given: 이미 댓글 좋아요가 존재
            commentLikeRepository.save(testUserId, testCommentId).block()

            // When & Then: 중복 생성 시 DataAccessException 발생
            assertThrows<DataAccessException> {
                commentLikeRepository.save(testUserId, testCommentId).block()
            }
        }
    }

    @Nested
    @DisplayName("delete - 댓글 좋아요 삭제")
    inner class Delete {

        @Test
        @DisplayName("댓글 좋아요를 삭제하면, deleted_at이 설정된다")
        fun delete_SetsDeletedAt() {
            // Given: 댓글 좋아요가 존재
            commentLikeRepository.save(testUserId, testCommentId).block()

            // When: 댓글 좋아요 삭제
            commentLikeRepository.delete(testUserId, testCommentId).block()

            // Then: deleted_at이 설정됨
            val exists = commentLikeRepository.exists(testUserId, testCommentId).block()!!
            assertFalse(exists)
        }

        @Test
        @DisplayName("존재하지 않는 댓글 좋아요를 삭제해도, 예외가 발생하지 않는다")
        fun delete_WhenNotExists_DoesNotThrow() {
            // Given: 댓글 좋아요가 존재하지 않음

            // When & Then: 삭제해도 예외 없음
            commentLikeRepository.delete(testUserId, testCommentId).block()
        }
    }

    @Nested
    @DisplayName("exists - 댓글 좋아요 존재 여부 확인")
    inner class Exists {

        @Test
        @DisplayName("댓글 좋아요가 존재하면, true를 반환한다")
        fun exists_WhenExists_ReturnsTrue() {
            // Given: 댓글 좋아요가 존재
            commentLikeRepository.save(testUserId, testCommentId).block()

            // When: 존재 여부 확인
            val exists = commentLikeRepository.exists(testUserId, testCommentId).block()!!

            // Then: true 반환
            assertTrue(exists)
        }

        @Test
        @DisplayName("댓글 좋아요가 존재하지 않으면, false를 반환한다")
        fun exists_WhenNotExists_ReturnsFalse() {
            // Given: 댓글 좋아요가 존재하지 않음

            // When: 존재 여부 확인
            val exists = commentLikeRepository.exists(testUserId, testCommentId).block()!!

            // Then: false 반환
            assertFalse(exists)
        }

        @Test
        @DisplayName("댓글 좋아요가 삭제되면, false를 반환한다")
        fun exists_WhenDeleted_ReturnsFalse() {
            // Given: 댓글 좋아요가 삭제됨
            commentLikeRepository.save(testUserId, testCommentId).block()
            commentLikeRepository.delete(testUserId, testCommentId).block()

            // When: 존재 여부 확인
            val exists = commentLikeRepository.exists(testUserId, testCommentId).block()!!

            // Then: false 반환
            assertFalse(exists)
        }
    }

    @Nested
    @DisplayName("findByUserIdAndCommentId - 댓글 좋아요 조회")
    inner class FindByUserIdAndCommentId {

        @Test
        @DisplayName("댓글 좋아요가 존재하면, 댓글 좋아요를 반환한다")
        fun findByUserIdAndCommentId_WhenExists_ReturnsCommentLike() {
            // Given: 댓글 좋아요가 존재
            commentLikeRepository.save(testUserId, testCommentId).block()

            // When: 댓글 좋아요 조회
            val commentLike = commentLikeRepository.findByUserIdAndCommentId(testUserId, testCommentId).block()

            // Then: 댓글 좋아요 반환
            assertNotNull(commentLike)
            assertEquals(testUserId, commentLike!!.userId)
            assertEquals(testCommentId, commentLike.commentId)
        }

        @Test
        @DisplayName("댓글 좋아요가 존재하지 않으면, null을 반환한다")
        fun findByUserIdAndCommentId_WhenNotExists_ReturnsNull() {
            // Given: 댓글 좋아요가 존재하지 않음

            // When: 댓글 좋아요 조회
            val commentLike = commentLikeRepository.findByUserIdAndCommentId(testUserId, testCommentId).block()

            // Then: null 반환
            assertEquals(null, commentLike)
        }
    }

    @Nested
    @DisplayName("countByCommentId - 댓글의 좋아요 수 조회")
    inner class CountByCommentId {

        @Test
        @DisplayName("댓글에 좋아요가 없으면, 0을 반환한다")
        fun countByCommentId_WhenNoLikes_ReturnsZero() {
            // Given: 댓글에 좋아요가 없음

            // When: 좋아요 수 조회
            val count = commentLikeRepository.countByCommentId(testCommentId).block()!!

            // Then: 0 반환
            assertEquals(0, count)
        }

        @Test
        @DisplayName("댓글에 좋아요가 있으면, 좋아요 수를 반환한다")
        fun countByCommentId_WhenLikesExist_ReturnsCount() {
            // Given: 댓글에 좋아요가 2개 있음
            commentLikeRepository.save(testUserId, testCommentId).block()!!

            val user2 = userRepository.save(
                me.onetwo.growsnap.domain.user.model.User(
                    email = "testuser2@test.com",
                    provider = OAuthProvider.GOOGLE,
                    providerId = "test-provider-id-2",
                    role = UserRole.USER
                )
            ).block()!!
            commentLikeRepository.save(user2.id!!, testCommentId).block()!!

            // When: 좋아요 수 조회
            val count = commentLikeRepository.countByCommentId(testCommentId).block()!!

            // Then: 2 반환
            assertEquals(2, count)
        }

        @Test
        @DisplayName("삭제된 좋아요는 카운트에 포함되지 않는다")
        fun countByCommentId_ExcludesDeletedLikes() {
            // Given: 댓글에 좋아요가 2개 있고, 1개는 삭제됨
            commentLikeRepository.save(testUserId, testCommentId).block()!!

            val user2 = userRepository.save(
                me.onetwo.growsnap.domain.user.model.User(
                    email = "testuser2@test.com",
                    provider = OAuthProvider.GOOGLE,
                    providerId = "test-provider-id-2",
                    role = UserRole.USER
                )
            ).block()!!
            commentLikeRepository.save(user2.id!!, testCommentId).block()!!
            commentLikeRepository.delete(user2.id!!, testCommentId).block()

            // When: 좋아요 수 조회
            val count = commentLikeRepository.countByCommentId(testCommentId).block()!!

            // Then: 1 반환 (삭제된 것 제외)
            assertEquals(1, count)
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

        // Contents 테이블 데이터 삽입
        Mono.from(dslContext.insertInto(CONTENTS)
            .set(CONTENTS.ID, contentId.toString())
            .set(CONTENTS.CREATOR_ID, creatorId.toString())
            .set(CONTENTS.CONTENT_TYPE, ContentType.VIDEO.name)
            .set(CONTENTS.URL, "https://example.com/video.mp4")
            .set(CONTENTS.THUMBNAIL_URL, "https://example.com/thumbnail.jpg")
            .set(CONTENTS.DURATION, 120)
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
            .set(CONTENT_METADATA.DESCRIPTION, "Test description")
            .set(CONTENT_METADATA.CATEGORY, "PROGRAMMING")
            .set(CONTENT_METADATA.TAGS, JSON.valueOf("[\"test\"]"))
            .set(CONTENT_METADATA.DIFFICULTY_LEVEL, "BEGINNER")
            .set(CONTENT_METADATA.LANGUAGE, "ko")
            .set(CONTENT_METADATA.CREATED_AT, now)
            .set(CONTENT_METADATA.CREATED_BY, creatorId.toString())
            .set(CONTENT_METADATA.UPDATED_AT, now)
            .set(CONTENT_METADATA.UPDATED_BY, creatorId.toString())).block()

        // Content_Interactions 테이블 (초기값 0)
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
}
