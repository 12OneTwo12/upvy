package me.onetwo.growsnap.domain.interaction.repository
import me.onetwo.growsnap.infrastructure.config.AbstractIntegrationTest

import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.interaction.model.Comment
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
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
import reactor.core.publisher.Mono
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("댓글 Repository 통합 테스트")
class CommentRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var commentRepository: CommentRepository

    @Autowired
    private lateinit var commentLikeRepository: CommentLikeRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    

    private lateinit var testUser: User
    private lateinit var testUser2: User
    private lateinit var testUser3: User
    private lateinit var testContentId: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트 데이터 준비
        testUser = userRepository.save(
            User(
                email = "user@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "user-123",
                role = UserRole.USER
            )
        ).block()!!

        testUser2 = userRepository.save(
            User(
                email = "user2@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "user2-123",
                role = UserRole.USER
            )
        ).block()!!

        testUser3 = userRepository.save(
            User(
                email = "user3@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "user3-123",
                role = UserRole.USER
            )
        ).block()!!

        testContentId = UUID.randomUUID()
        insertContent(testContentId, testUser.id!!, "Test Video")
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
    @DisplayName("save - 댓글 생성")
    inner class Save {

        @Test
        @DisplayName("댓글을 생성하면, 데이터베이스에 저장된다")
        fun save_CreatesComment() {
            // Given: 댓글 데이터
            val comment = Comment(
                contentId = testContentId,
                userId = testUser.id!!,
                content = "Test comment",
                parentCommentId = null
            )

            // When: 댓글 저장
            val saved = commentRepository.save(comment).block()!!

            // Then: 저장된 댓글 확인
            assertNotNull(saved)
            assertNotNull(saved.id)
            assertEquals(testContentId, saved.contentId)
            assertEquals(testUser.id!!, saved.userId)
            assertEquals("Test comment", saved.content)
        }

        @Test
        @DisplayName("대댓글을 생성하면, parentCommentId가 저장된다")
        fun save_WithParent_SavesParentCommentId() {
            // Given: 부모 댓글 생성
            val parentComment = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Parent comment",
                    parentCommentId = null
                )
            ).block()!!

            // 대댓글 생성
            val reply = Comment(
                contentId = testContentId,
                userId = testUser.id!!,
                content = "Reply comment",
                parentCommentId = parentComment.id
            )

            // When: 대댓글 저장
            val saved = commentRepository.save(reply).block()!!

            // Then: parentCommentId 확인
            assertNotNull(saved)
            assertEquals(parentComment.id, saved.parentCommentId)
        }
    }

    @Nested
    @DisplayName("findById - ID로 댓글 조회")
    inner class FindById {

        @Test
        @DisplayName("존재하는 댓글 ID로 조회하면, 댓글이 반환된다")
        fun findById_ExistingId_ReturnsComment() {
            // Given: 댓글 생성
            val comment = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Test comment",
                    parentCommentId = null
                )
            ).block()!!

            // When: ID로 조회
            val found = commentRepository.findById(comment.id!!).block()

            // Then: 댓글 반환 확인
            assertNotNull(found)
            assertEquals(comment.id, found!!.id)
            assertEquals(comment.content, found.content)
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면, null이 반환된다")
        fun findById_NonExistingId_ReturnsNull() {
            // Given: 존재하지 않는 ID
            val nonExistingId = UUID.randomUUID()

            // When: 조회
            val found = commentRepository.findById(nonExistingId).block()

            // Then: null 반환
            assertNull(found)
        }
    }

    @Nested
    @DisplayName("findByContentId - 콘텐츠의 댓글 목록 조회")
    inner class FindByContentId {

        @Test
        @DisplayName("콘텐츠의 모든 댓글을 조회하면, 생성일시 오름차순으로 반환된다")
        fun findByContentId_ReturnsCommentsOrderedByCreatedAt() {
            // Given: 3개의 댓글 생성
            commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "First comment",
                    parentCommentId = null
                )
            ).block()!!

            // created_at은 millisecond 단위이므로 시간 차이 자동 보장

            commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Second comment",
                    parentCommentId = null
                )
            ).block()!!


            commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Third comment",
                    parentCommentId = null
                )
            ).block()!!

            // When: 댓글 목록 조회
            val comments = commentRepository.findByContentId(testContentId).collectList().block()!!

            // Then: 3개의 댓글이 생성 시간 순서대로 반환
            assertNotNull(comments)
            assertEquals(3, comments.size)
            assertEquals("First comment", comments[0].content)
            assertEquals("Second comment", comments[1].content)
            assertEquals("Third comment", comments[2].content)
        }

        @Test
        @DisplayName("삭제된 댓글은 조회되지 않는다")
        fun findByContentId_ExcludesDeletedComments() {
            // Given: 댓글 생성 후 삭제
            val comment = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Comment to be deleted",
                    parentCommentId = null
                )
            ).block()!!

            commentRepository.delete(comment.id!!, testUser.id!!).block()

            // When: 댓글 목록 조회
            val comments = commentRepository.findByContentId(testContentId).collectList().block()!!

            // Then: 삭제된 댓글은 조회되지 않음
            assertNotNull(comments)
            assertEquals(0, comments.size)
        }
    }

    @Nested
    @DisplayName("findByParentCommentId - 부모 댓글의 대댓글 조회")
    inner class FindByParentCommentId {

        @Test
        @DisplayName("부모 댓글의 대댓글을 조회하면, 모든 대댓글이 반환된다")
        fun findByParentCommentId_ReturnsAllReplies() {
            // Given: 부모 댓글 생성
            val parentComment = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Parent comment",
                    parentCommentId = null
                )
            ).block()!!

            // 대댓글 2개 생성
            commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Reply 1",
                    parentCommentId = parentComment.id
                )
            ).block()!!

            commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Reply 2",
                    parentCommentId = parentComment.id
                )
            ).block()!!

            // When: 대댓글 조회
            val replies = commentRepository.findByParentCommentId(parentComment.id!!).collectList().block()!!

            // Then: 2개의 대댓글 반환
            assertNotNull(replies)
            assertEquals(2, replies.size)
        }
    }

    @Nested
    @DisplayName("delete - 댓글 삭제")
    inner class Delete {

        @Test
        @DisplayName("댓글을 삭제하면, Soft Delete가 적용된다")
        fun delete_AppliesSoftDelete() {
            // Given: 댓글 생성
            val comment = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Comment to delete",
                    parentCommentId = null
                )
            ).block()!!

            // When: 댓글 삭제
            commentRepository.delete(comment.id!!, testUser.id!!).block()

            // Then: findById로 조회되지 않음 (deletedAt이 설정됨)
            val found = commentRepository.findById(comment.id!!).block()
            assertNull(found)
        }
    }

    @Nested
    @DisplayName("findTopLevelCommentsByContentId - 최상위 댓글 페이징 조회")
    inner class FindTopLevelCommentsByContentId {

        @Test
        @DisplayName("cursor 없이 조회하면, 처음부터 limit만큼 반환된다")
        fun findTopLevelComments_NoCursor_ReturnsFirstPage() {
            // Given: 5개의 최상위 댓글 생성
            repeat(5) { index ->
                commentRepository.save(
                    Comment(
                        contentId = testContentId,
                        userId = testUser.id!!,
                        content = "Comment $index",
                        parentCommentId = null
                    )
                ).block()!!
                // created_at은 millisecond 단위이므로 시간 차이 자동 보장
            }

            // When: limit 3으로 조회
            val comments = commentRepository.findTopLevelCommentsByContentId(testContentId, null, 3).collectList().block()!!

            // Then: 4개 반환 (limit + 1 for hasNext check)
            assertEquals(4, comments.size)
            assertEquals("Comment 0", comments[0].content)
            assertEquals("Comment 1", comments[1].content)
            assertEquals("Comment 2", comments[2].content)
        }

        @Test
        @DisplayName("cursor를 지정하면, 해당 위치 이후부터 조회된다")
        fun findTopLevelComments_WithCursor_ReturnsAfterCursor() {
            // Given: 5개의 최상위 댓글 생성
            repeat(5) { index ->
                commentRepository.save(
                    Comment(
                        contentId = testContentId,
                        userId = testUser.id!!,
                        content = "Comment $index",
                        parentCommentId = null
                    )
                ).block()!!
            }

            // DB에서 정렬된 순서대로 전체 목록을 가져옴
            val allComments = commentRepository.findTopLevelCommentsByContentId(testContentId, null, 10).collectList().block()!!

            // When: 두 번째 댓글을 cursor로 설정하고 조회
            val cursorIndex = 1
            val cursor = allComments[cursorIndex].id!!

            // 디버깅: findById로 cursor 댓글이 조회되는지 확인
            val cursorComment = commentRepository.findById(cursor).block()
            assertNotNull(cursorComment, "cursor 댓글을 findById로 조회할 수 있어야 함. cursor=$cursor")

            val comments = commentRepository.findTopLevelCommentsByContentId(testContentId, cursor, 3).collectList().block()!!

            // Then: cursor 이후의 댓글들이 조회됨
            // allComments에서 cursor 다음부터의 댓글들 (최대 3개, 또는 limit+1로 4개까지)
            val expectedAfterCursor = allComments.drop(cursorIndex + 1)

            // 실제로 cursor 이후에 남은 댓글 수에 따라 결과가 달라짐
            // 5개 중 cursor는 index 1이므로, 이후는 index 2,3,4 (3개)
            // limit=3이고 repository는 limit+1=4개를 요청하지만 3개만 있으므로 3개 반환
            assertNotNull(comments, "comments should not be null")
            assertEquals(
                expectedAfterCursor.size,
                comments.size,
                "allComments=${ allComments.map { it.content } }, cursor=${allComments[cursorIndex].content}, " +
                "expected=${expectedAfterCursor.map { it.content }}, actual=${comments.map { it.content }}"
            )

            // 순서 확인
            expectedAfterCursor.forEachIndexed { index, expected ->
                assertEquals(expected.content, comments[index].content)
            }
        }

        @Test
        @DisplayName("대댓글은 제외하고 최상위 댓글만 조회된다")
        fun findTopLevelComments_ExcludesReplies() {
            // Given: 최상위 댓글과 대댓글 생성
            val parentComment = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Parent comment",
                    parentCommentId = null
                )
            ).block()!!

            // 대댓글 생성
            commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Reply comment",
                    parentCommentId = parentComment.id
                )
            ).block()!!

            // When: 최상위 댓글 조회
            val comments = commentRepository.findTopLevelCommentsByContentId(testContentId, null, 10).collectList().block()!!

            // Then: 최상위 댓글만 조회됨
            assertEquals(1, comments.size)
            assertEquals("Parent comment", comments[0].content)
        }

        @Test
        @DisplayName("인기순(좋아요 + 대댓글 수) 내림차순으로 정렬된다")
        fun findTopLevelComments_OrderedByPopularityScore() {
            // Given: 3개의 댓글 생성
            val comment1 = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Comment with 1 like",
                    parentCommentId = null
                )
            ).block()!!

            val comment2 = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Comment with 2 likes + 1 reply",
                    parentCommentId = null
                )
            ).block()!!

            commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Comment with no interactions",
                    parentCommentId = null
                )
            ).block()!!

            // 좋아요 추가: comment1(1개), comment2(2개)
            commentLikeRepository.save(testUser2.id!!, comment1.id!!).block()
            commentLikeRepository.save(testUser2.id!!, comment2.id!!).block()
            commentLikeRepository.save(testUser3.id!!, comment2.id!!).block()

            // 대댓글 추가: comment2(1개)
            commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Reply to comment2",
                    parentCommentId = comment2.id
                )
            ).block()!!

            // When: 최상위 댓글 조회
            val comments = commentRepository.findTopLevelCommentsByContentId(testContentId, null, 10).collectList().block()!!

            // Then: 인기순 정렬 확인
            // comment2 (인기점수 3 = 좋아요 2 + 대댓글 1)
            // comment1 (인기점수 1 = 좋아요 1)
            // comment3 (인기점수 0 = 좋아요 0 + 대댓글 0)
            assertEquals(3, comments.size)
            assertEquals("Comment with 2 likes + 1 reply", comments[0].content)
            assertEquals("Comment with 1 like", comments[1].content)
            assertEquals("Comment with no interactions", comments[2].content)
        }

        @Test
        @DisplayName("인기 점수가 같으면 오래된순으로 정렬된다")
        fun findTopLevelComments_SameScore_OrderedByCreatedAtDesc() {
            // Given: 인기 점수가 같은 2개의 댓글 생성
            val olderComment = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Older comment",
                    parentCommentId = null
                )
            ).block()!!
            // created_at은 millisecond 단위이므로 시간 차이 자동 보장

            val newerComment = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Newer comment",
                    parentCommentId = null
                )
            ).block()!!

            // 두 댓글 모두 좋아요 1개씩
            commentLikeRepository.save(testUser2.id!!, olderComment.id!!).block()
            commentLikeRepository.save(testUser2.id!!, newerComment.id!!).block()

            // When: 최상위 댓글 조회
            val comments = commentRepository.findTopLevelCommentsByContentId(testContentId, null, 10).collectList().block()!!

            // Then: 같은 인기 점수면 최신순
            assertEquals(2, comments.size)
            assertEquals("Older comment", comments[0].content)
            assertEquals("Newer comment", comments[1].content)
        }
    }

    @Nested
    @DisplayName("findRepliesByParentCommentId - 대댓글 페이징 조회")
    inner class FindRepliesByParentCommentId {

        @Test
        @DisplayName("cursor 없이 조회하면, 처음부터 limit만큼 반환된다")
        fun findReplies_NoCursor_ReturnsFirstPage() {
            // Given: 부모 댓글 생성
            val parentComment = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Parent comment",
                    parentCommentId = null
                )
            ).block()!!

            // 5개의 대댓글 생성
            repeat(5) { index ->
                commentRepository.save(
                    Comment(
                        contentId = testContentId,
                        userId = testUser.id!!,
                        content = "Reply $index",
                        parentCommentId = parentComment.id
                    )
                ).block()!!
            }

            // When: limit 3으로 조회
            val replies = commentRepository.findRepliesByParentCommentId(parentComment.id!!, null, 3).collectList().block()!!

            // Then: 4개 반환 (limit + 1 for hasNext check)
            assertEquals(4, replies.size)
            assertEquals("Reply 0", replies[0].content)
            assertEquals("Reply 1", replies[1].content)
            assertEquals("Reply 2", replies[2].content)
        }

        @Test
        @DisplayName("cursor를 지정하면, 해당 위치 이후부터 조회된다")
        fun findReplies_WithCursor_ReturnsAfterCursor() {
            // Given: 부모 댓글 생성
            val parentComment = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Parent comment",
                    parentCommentId = null
                )
            ).block()!!

            // 5개의 대댓글 생성
            repeat(5) { index ->
                commentRepository.save(
                    Comment(
                        contentId = testContentId,
                        userId = testUser.id!!,
                        content = "Reply $index",
                        parentCommentId = parentComment.id
                    )
                ).block()!!
            }

            // DB에서 정렬된 순서대로 전체 목록을 가져옴
            val allReplies = commentRepository.findRepliesByParentCommentId(parentComment.id!!, null, 10).collectList().block()!!

            // When: 두 번째 대댓글을 cursor로 설정하고 조회
            val cursorIndex = 1
            val cursor = allReplies[cursorIndex].id!!

            // 디버깅: findById로 cursor 댓글이 조회되는지 확인
            val cursorReply = commentRepository.findById(cursor).block()
            assertNotNull(cursorReply, "cursor 대댓글을 findById로 조회할 수 있어야 함. cursor=$cursor")

            val replies = commentRepository.findRepliesByParentCommentId(parentComment.id!!, cursor, 3).collectList().block()!!

            // Then: cursor 이후의 대댓글들이 조회됨
            val expectedAfterCursor = allReplies.drop(cursorIndex + 1)

            assertNotNull(replies, "replies should not be null")
            assertEquals(
                expectedAfterCursor.size,
                replies.size,
                "allReplies=${allReplies.map { it.content }}, cursor=${allReplies[cursorIndex].content}, " +
                "expected=${expectedAfterCursor.map { it.content }}, actual=${replies.map { it.content }}"
            )

            // 순서 확인
            expectedAfterCursor.forEachIndexed { index, expected ->
                assertEquals(expected.content, replies[index].content)
            }
        }
    }

    @Nested
    @DisplayName("countRepliesByParentCommentId - 대댓글 개수 조회")
    inner class CountRepliesByParentCommentId {

        @Test
        @DisplayName("대댓글이 없으면, 0을 반환한다")
        fun countReplies_NoReplies_ReturnsZero() {
            // Given: 부모 댓글만 생성
            val parentComment = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Parent comment",
                    parentCommentId = null
                )
            ).block()!!

            // When: 대댓글 개수 조회
            val count = commentRepository.countRepliesByParentCommentId(parentComment.id!!).block()!!

            // Then: 0 반환
            assertEquals(0, count)
        }

        @Test
        @DisplayName("대댓글이 있으면, 정확한 개수를 반환한다")
        fun countReplies_WithReplies_ReturnsCorrectCount() {
            // Given: 부모 댓글 생성
            val parentComment = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Parent comment",
                    parentCommentId = null
                )
            ).block()!!

            // 3개의 대댓글 생성
            repeat(3) { index ->
                commentRepository.save(
                    Comment(
                        contentId = testContentId,
                        userId = testUser.id!!,
                        content = "Reply $index",
                        parentCommentId = parentComment.id
                    )
                ).block()!!
            }

            // When: 대댓글 개수 조회
            val count = commentRepository.countRepliesByParentCommentId(parentComment.id!!).block()!!

            // Then: 3 반환
            assertEquals(3, count)
        }

        @Test
        @DisplayName("삭제된 대댓글은 카운트에서 제외된다")
        fun countReplies_ExcludesDeletedReplies() {
            // Given: 부모 댓글 생성
            val parentComment = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Parent comment",
                    parentCommentId = null
                )
            ).block()!!

            // 2개의 대댓글 생성
            val reply1 = commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Reply 1",
                    parentCommentId = parentComment.id
                )
            ).block()!!

            commentRepository.save(
                Comment(
                    contentId = testContentId,
                    userId = testUser.id!!,
                    content = "Reply 2",
                    parentCommentId = parentComment.id
                )
            ).block()!!

            // 첫 번째 대댓글 삭제
            commentRepository.delete(reply1.id!!, testUser.id!!).block()

            // When: 대댓글 개수 조회
            val count = commentRepository.countRepliesByParentCommentId(parentComment.id!!).block()!!

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
