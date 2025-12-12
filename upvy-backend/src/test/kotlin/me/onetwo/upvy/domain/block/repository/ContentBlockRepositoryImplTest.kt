package me.onetwo.upvy.domain.block.repository
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest

import me.onetwo.upvy.domain.content.model.Category
import me.onetwo.upvy.domain.content.model.ContentStatus
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.domain.user.model.OAuthProvider
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.jooq.generated.tables.ContentBlocks.Companion.CONTENT_BLOCKS
import me.onetwo.upvy.jooq.generated.tables.ContentMetadata.Companion.CONTENT_METADATA
import me.onetwo.upvy.jooq.generated.tables.Contents.Companion.CONTENTS
import me.onetwo.upvy.jooq.generated.tables.UserProfiles.Companion.USER_PROFILES
import me.onetwo.upvy.jooq.generated.tables.Users.Companion.USERS
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * 콘텐츠 차단 Repository 통합 테스트
 *
 * ContentBlockRepositoryImpl의 데이터베이스 CRUD 동작을 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("콘텐츠 차단 Repository 통합 테스트")
class ContentBlockRepositoryImplTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var contentBlockRepository: ContentBlockRepository

    

    private lateinit var userId: UUID
    private lateinit var contentId: UUID
    private lateinit var creatorId: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트용 사용자와 콘텐츠 생성
        val uniqueSuffix = UUID.randomUUID().toString().substring(0, 8)
        userId = insertUser("user-$uniqueSuffix@test.com", "google-user-$uniqueSuffix")
        creatorId = insertUser("creator-$uniqueSuffix@test.com", "google-creator-$uniqueSuffix")
        insertUserProfile(creatorId, "Creator", null)
        contentId = insertContent(creatorId, "Test Content")
    }

    @AfterEach
    fun tearDown() {
        // Given: 테스트 데이터 정리

        // When: 모든 테스트 데이터 삭제
        Mono.from(dslContext.deleteFrom(CONTENT_BLOCKS)).block()
        Mono.from(dslContext.deleteFrom(CONTENT_METADATA)).block()
        Mono.from(dslContext.deleteFrom(CONTENTS)).block()
        Mono.from(dslContext.deleteFrom(USER_PROFILES)).block()
        Mono.from(dslContext.deleteFrom(USERS)).block()
    }

    @Nested
    @DisplayName("save - 콘텐츠 차단 생성")
    inner class Save {

        @Test
        @DisplayName("콘텐츠 차단을 생성한다")
        fun save_CreatesContentBlock() {
            // Given: 차단할 콘텐츠 정보

            // When: 콘텐츠 차단 생성
            val result = contentBlockRepository.save(userId, contentId)

            // Then: 차단이 성공적으로 생성됨
            StepVerifier.create(result)
                .assertNext { contentBlock ->
                    assertThat(contentBlock.id).isNotNull()
                    assertThat(contentBlock.userId).isEqualTo(userId)
                    assertThat(contentBlock.contentId).isEqualTo(contentId)
                    assertThat(contentBlock.createdAt).isNotNull()
                    assertThat(contentBlock.createdBy).isEqualTo(userId.toString())
                    assertThat(contentBlock.updatedAt).isNotNull()
                    assertThat(contentBlock.updatedBy).isEqualTo(userId.toString())
                    assertThat(contentBlock.deletedAt).isNull()
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("Audit Trail이 자동으로 설정된다")
        fun save_SetsAuditTrailAutomatically() {
            // Given: 차단할 콘텐츠 정보

            // When: 콘텐츠 차단 생성
            val result = contentBlockRepository.save(userId, contentId)

            // Then: Audit Trail 필드가 자동으로 설정됨
            StepVerifier.create(result)
                .assertNext { contentBlock ->
                    assertThat(contentBlock.createdAt).isNotNull()
                    assertThat(contentBlock.createdBy).isEqualTo(userId.toString())
                    assertThat(contentBlock.updatedAt).isNotNull()
                    assertThat(contentBlock.updatedBy).isEqualTo(userId.toString())
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("삭제된 차단을 다시 차단하면 복원된다 (UPSERT)")
        fun save_WhenBlockIsDeleted_ResurrectBlock() {
            // Given: 콘텐츠 차단 생성 후 삭제
            contentBlockRepository.save(userId, contentId).block()!!
            contentBlockRepository.delete(userId, contentId).block()

            // When: 삭제된 차단을 다시 차단
            val result = contentBlockRepository.save(userId, contentId)

            // Then: 차단이 복원됨 (deleted_at = NULL)
            StepVerifier.create(result)
                .assertNext { contentBlock ->
                    assertThat(contentBlock.userId).isEqualTo(userId)
                    assertThat(contentBlock.contentId).isEqualTo(contentId)
                    assertThat(contentBlock.deletedAt).isNull()  // 복원됨
                }
                .verifyComplete()

            // Then: exists()로 확인해도 존재함
            val exists = contentBlockRepository.exists(userId, contentId).block()
            assertThat(exists).isTrue()
        }

        @Test
        @DisplayName("이미 차단한 콘텐츠를 다시 차단해도 성공한다 (Idempotent)")
        fun save_WhenAlreadyBlocked_IsIdempotent() {
            // Given: 이미 차단한 콘텐츠
            contentBlockRepository.save(userId, contentId).block()

            // When: 동일한 콘텐츠를 다시 차단 시도
            val result = contentBlockRepository.save(userId, contentId)

            // Then: 성공적으로 완료됨 (중복 에러 없음)
            StepVerifier.create(result)
                .assertNext { contentBlock ->
                    assertThat(contentBlock.userId).isEqualTo(userId)
                    assertThat(contentBlock.contentId).isEqualTo(contentId)
                    assertThat(contentBlock.deletedAt).isNull()
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("exists - 콘텐츠 차단 존재 여부 확인")
    inner class Exists {

        @Test
        @DisplayName("차단이 존재하면 true를 반환한다")
        fun exists_WhenBlockExists_ReturnsTrue() {
            // Given: 콘텐츠 차단 생성
            contentBlockRepository.save(userId, contentId).block()

            // When: 차단 존재 여부 확인
            val result = contentBlockRepository.exists(userId, contentId)

            // Then: true 반환
            StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete()
        }

        @Test
        @DisplayName("차단이 존재하지 않으면 false를 반환한다")
        fun exists_WhenBlockDoesNotExist_ReturnsFalse() {
            // Given: 차단이 없는 상태

            // When: 차단 존재 여부 확인
            val result = contentBlockRepository.exists(userId, contentId)

            // Then: false 반환
            StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete()
        }

        @Test
        @DisplayName("삭제된 차단은 존재하지 않는 것으로 간주한다")
        fun exists_WhenBlockIsDeleted_ReturnsFalse() {
            // Given: 콘텐츠 차단 생성 후 삭제
            contentBlockRepository.save(userId, contentId).block()
            contentBlockRepository.delete(userId, contentId).block()

            // When: 차단 존재 여부 확인
            val result = contentBlockRepository.exists(userId, contentId)

            // Then: false 반환 (Soft Delete)
            StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findByUserIdAndContentId - 콘텐츠 차단 조회")
    inner class FindByUserIdAndContentId {

        @Test
        @DisplayName("차단이 존재하면 ContentBlock을 반환한다")
        fun findByUserIdAndContentId_WhenBlockExists_ReturnsContentBlock() {
            // Given: 콘텐츠 차단 생성
            val saved = contentBlockRepository.save(userId, contentId).block()!!

            // When: 차단 조회
            val result = contentBlockRepository.findByUserIdAndContentId(userId, contentId)

            // Then: ContentBlock 반환
            StepVerifier.create(result)
                .assertNext { contentBlock ->
                    assertThat(contentBlock.id).isEqualTo(saved.id)
                    assertThat(contentBlock.userId).isEqualTo(userId)
                    assertThat(contentBlock.contentId).isEqualTo(contentId)
                    assertThat(contentBlock.deletedAt).isNull()
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("차단이 존재하지 않으면 empty를 반환한다")
        fun findByUserIdAndContentId_WhenBlockDoesNotExist_ReturnsEmpty() {
            // Given: 차단이 없는 상태

            // When: 차단 조회
            val result = contentBlockRepository.findByUserIdAndContentId(userId, contentId)

            // Then: empty 반환
            StepVerifier.create(result)
                .verifyComplete()
        }

        @Test
        @DisplayName("삭제된 차단도 조회된다 (UPSERT 지원)")
        fun findByUserIdAndContentId_WhenBlockIsDeleted_ReturnsDeletedBlock() {
            // Given: 콘텐츠 차단 생성 후 삭제
            contentBlockRepository.save(userId, contentId).block()
            contentBlockRepository.delete(userId, contentId).block()

            // When: 차단 조회
            val result = contentBlockRepository.findByUserIdAndContentId(userId, contentId)

            // Then: 삭제된 차단 반환 (UPSERT 패턴 지원)
            StepVerifier.create(result)
                .assertNext { contentBlock ->
                    assertThat(contentBlock.userId).isEqualTo(userId)
                    assertThat(contentBlock.contentId).isEqualTo(contentId)
                    assertThat(contentBlock.deletedAt).isNotNull()  // 삭제된 상태
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("delete - 콘텐츠 차단 삭제 (Soft Delete)")
    inner class Delete {

        @Test
        @DisplayName("차단을 소프트 삭제한다")
        fun delete_SoftDeletesBlock() {
            // Given: 콘텐츠 차단 생성
            val saved = contentBlockRepository.save(userId, contentId).block()!!

            // When: 차단 삭제
            val result = contentBlockRepository.delete(userId, contentId)

            // Then: 삭제 성공
            StepVerifier.create(result)
                .verifyComplete()

            // Then: deleted_at이 설정됨 (Soft Delete 확인)
            val deletedBlock = Mono.from(
                dslContext
                    .select(CONTENT_BLOCKS.DELETED_AT)
                    .from(CONTENT_BLOCKS)
                    .where(CONTENT_BLOCKS.ID.eq(saved.id))
            ).block()

            assertThat(deletedBlock).isNotNull()
            assertThat(deletedBlock!!.getValue(CONTENT_BLOCKS.DELETED_AT)).isNotNull()
        }

        @Test
        @DisplayName("존재하지 않는 차단 삭제 시 성공적으로 완료된다")
        fun delete_WhenBlockDoesNotExist_CompletesSuccessfully() {
            // Given: 차단이 없는 상태

            // When: 차단 삭제 시도
            val result = contentBlockRepository.delete(userId, contentId)

            // Then: 에러 없이 완료
            StepVerifier.create(result)
                .verifyComplete()
        }

        @Test
        @DisplayName("삭제 시 updated_at과 updated_by가 업데이트된다")
        fun delete_UpdatesAuditTrail() {
            // Given: 콘텐츠 차단 생성
            val saved = contentBlockRepository.save(userId, contentId).block()!!
            val originalUpdatedAt = saved.updatedAt

            // When: 차단 삭제
            contentBlockRepository.delete(userId, contentId).block()

            // Then: updated_at과 updated_by가 업데이트됨
            val deletedBlock = Mono.from(
                dslContext
                    .select(CONTENT_BLOCKS.UPDATED_AT, CONTENT_BLOCKS.UPDATED_BY, CONTENT_BLOCKS.DELETED_AT)
                    .from(CONTENT_BLOCKS)
                    .where(CONTENT_BLOCKS.ID.eq(saved.id))
            ).block()

            assertThat(deletedBlock).isNotNull()
            assertThat(deletedBlock!!.getValue(CONTENT_BLOCKS.UPDATED_AT)).isAfter(originalUpdatedAt)
            assertThat(deletedBlock.getValue(CONTENT_BLOCKS.UPDATED_BY)).isEqualTo(userId.toString())
            assertThat(deletedBlock.getValue(CONTENT_BLOCKS.DELETED_AT)).isNotNull()
        }
    }

    @Nested
    @DisplayName("findBlockedContentsByUserId - 차단한 콘텐츠 목록 조회")
    inner class FindBlockedContentsByUserId {

        @Test
        @DisplayName("차단한 콘텐츠 목록을 반환한다")
        fun findBlockedContentsByUserId_ReturnsBlockedContents() {
            // Given: 여러 콘텐츠 차단
            val content1 = insertContent(creatorId, "Content 1")
            val content2 = insertContent(creatorId, "Content 2")
            val content3 = insertContent(creatorId, "Content 3")

            contentBlockRepository.save(userId, content1).block()
            contentBlockRepository.save(userId, content2).block()
            contentBlockRepository.save(userId, content3).block()

            // When: 차단한 콘텐츠 목록 조회
            val result = contentBlockRepository.findBlockedContentsByUserId(userId, null, 10)

            // Then: 3개의 차단된 콘텐츠 반환 (최신순)
            StepVerifier.create(result.collectList())
                .assertNext { items ->
                    assertThat(items).hasSize(3)
                    assertThat(items[0].contentId).isEqualTo(content3.toString())
                    assertThat(items[0].title).isEqualTo("Content 3")
                    assertThat(items[1].contentId).isEqualTo(content2.toString())
                    assertThat(items[1].title).isEqualTo("Content 2")
                    assertThat(items[2].contentId).isEqualTo(content1.toString())
                    assertThat(items[2].title).isEqualTo("Content 1")
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("커서 기반 페이지네이션이 동작한다")
        fun findBlockedContentsByUserId_WithCursor_ReturnsPaginatedResults() {
            // Given: 여러 콘텐츠 차단
            val content1 = insertContent(creatorId, "Content 1")
            val content2 = insertContent(creatorId, "Content 2")
            val content3 = insertContent(creatorId, "Content 3")

            contentBlockRepository.save(userId, content1).block()
            val block2 = contentBlockRepository.save(userId, content2).block()!!
            contentBlockRepository.save(userId, content3).block()

            // When: 커서로 두 번째 차단 이후 조회
            val result = contentBlockRepository.findBlockedContentsByUserId(userId, block2.id, 10)

            // Then: 첫 번째 차단만 반환
            StepVerifier.create(result.collectList())
                .assertNext { items ->
                    assertThat(items).hasSize(1)
                    assertThat(items[0].contentId).isEqualTo(content1.toString())
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("limit이 적용된다")
        fun findBlockedContentsByUserId_WithLimit_ReturnsLimitedResults() {
            // Given: 여러 콘텐츠 차단
            val content1 = insertContent(creatorId, "Content 1")
            val content2 = insertContent(creatorId, "Content 2")
            val content3 = insertContent(creatorId, "Content 3")

            contentBlockRepository.save(userId, content1).block()
            contentBlockRepository.save(userId, content2).block()
            contentBlockRepository.save(userId, content3).block()

            // When: limit 2로 조회
            val result = contentBlockRepository.findBlockedContentsByUserId(userId, null, 2)

            // Then: 2개만 반환
            StepVerifier.create(result.collectList())
                .assertNext { items ->
                    assertThat(items).hasSize(2)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("삭제된 차단은 조회되지 않는다")
        fun findBlockedContentsByUserId_ExcludesDeletedBlocks() {
            // Given: 여러 콘텐츠 차단 후 하나 삭제
            val content1 = insertContent(creatorId, "Content 1")
            val content2 = insertContent(creatorId, "Content 2")

            contentBlockRepository.save(userId, content1).block()
            contentBlockRepository.save(userId, content2).block()
            contentBlockRepository.delete(userId, content2).block()

            // When: 차단한 콘텐츠 목록 조회
            val result = contentBlockRepository.findBlockedContentsByUserId(userId, null, 10)

            // Then: 삭제되지 않은 차단만 반환
            StepVerifier.create(result.collectList())
                .assertNext { items ->
                    assertThat(items).hasSize(1)
                    assertThat(items[0].contentId).isEqualTo(content1.toString())
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("차단한 콘텐츠가 없으면 빈 목록을 반환한다")
        fun findBlockedContentsByUserId_WhenNoBlocks_ReturnsEmptyList() {
            // Given: 차단이 없는 상태

            // When: 차단한 콘텐츠 목록 조회
            val result = contentBlockRepository.findBlockedContentsByUserId(userId, null, 10)

            // Then: 빈 목록 반환
            StepVerifier.create(result.collectList())
                .assertNext { items ->
                    assertThat(items).isEmpty()
                }
                .verifyComplete()
        }
    }

    /**
     * 테스트용 사용자 생성 헬퍼 메서드
     */
    private fun insertUser(email: String, providerId: String): UUID {
        val userId = UUID.randomUUID()
        val now = Instant.now()

        Mono.from(
            dslContext.insertInto(USERS)
                .set(USERS.ID, userId.toString())
                .set(USERS.EMAIL, email)
                .set(USERS.ROLE, UserRole.USER.name)
                .set(USERS.CREATED_AT, now)
                .set(USERS.CREATED_BY, userId.toString())
                .set(USERS.UPDATED_AT, now)
                .set(USERS.UPDATED_BY, userId.toString())
        ).block()

        return userId
    }

    /**
     * 테스트용 사용자 프로필 생성 헬퍼 메서드
     */
    private fun insertUserProfile(userId: UUID, nickname: String, profileImageUrl: String?) {
        val now = Instant.now()

        Mono.from(
            dslContext.insertInto(USER_PROFILES)
                .set(USER_PROFILES.USER_ID, userId.toString())
                .set(USER_PROFILES.NICKNAME, nickname)
                .set(USER_PROFILES.PROFILE_IMAGE_URL, profileImageUrl)
                .set(USER_PROFILES.CREATED_AT, now)
                .set(USER_PROFILES.CREATED_BY, userId.toString())
                .set(USER_PROFILES.UPDATED_AT, now)
                .set(USER_PROFILES.UPDATED_BY, userId.toString())
        ).block()
    }

    /**
     * 테스트용 콘텐츠 생성 헬퍼 메서드
     */
    private fun insertContent(creatorId: UUID, title: String): UUID {
        val contentId = UUID.randomUUID()
        val now = Instant.now()

        Mono.from(
            dslContext.insertInto(CONTENTS)
                .set(CONTENTS.ID, contentId.toString())
                .set(CONTENTS.CREATOR_ID, creatorId.toString())
                .set(CONTENTS.CONTENT_TYPE, ContentType.VIDEO.name)
                .set(CONTENTS.URL, "https://example.com/video.mp4")
                .set(CONTENTS.STATUS, ContentStatus.PUBLISHED.name)
                .set(CONTENTS.THUMBNAIL_URL, "https://example.com/thumbnail.jpg")
                .set(CONTENTS.WIDTH, 1920)
                .set(CONTENTS.HEIGHT, 1080)
                .set(CONTENTS.CREATED_AT, now)
                .set(CONTENTS.CREATED_BY, creatorId.toString())
                .set(CONTENTS.UPDATED_AT, now)
                .set(CONTENTS.UPDATED_BY, creatorId.toString())
        ).block()

        Mono.from(
            dslContext.insertInto(CONTENT_METADATA)
                .set(CONTENT_METADATA.CONTENT_ID, contentId.toString())
                .set(CONTENT_METADATA.TITLE, title)
                .set(CONTENT_METADATA.CATEGORY, Category.PROGRAMMING.name)
                .set(CONTENT_METADATA.CREATED_AT, now)
                .set(CONTENT_METADATA.CREATED_BY, creatorId.toString())
                .set(CONTENT_METADATA.UPDATED_AT, now)
                .set(CONTENT_METADATA.UPDATED_BY, creatorId.toString())
        ).block()

        return contentId
    }
}
