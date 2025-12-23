package me.onetwo.upvy.domain.interaction.repository
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest

import me.onetwo.upvy.domain.content.model.ContentStatus
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.jooq.generated.tables.ContentInteractions.Companion.CONTENT_INTERACTIONS
import me.onetwo.upvy.jooq.generated.tables.ContentMetadata.Companion.CONTENT_METADATA
import me.onetwo.upvy.jooq.generated.tables.ContentPhotos.Companion.CONTENT_PHOTOS
import me.onetwo.upvy.jooq.generated.tables.Contents.Companion.CONTENTS
import me.onetwo.upvy.jooq.generated.tables.UserProfiles.Companion.USER_PROFILES
import me.onetwo.upvy.jooq.generated.tables.UserSaves.Companion.USER_SAVES
import me.onetwo.upvy.jooq.generated.tables.Users.Companion.USERS
import org.jooq.DSLContext
import org.jooq.JSON
import reactor.core.publisher.Mono
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

/**
 * UserSaveRepository 통합 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UserSaveRepository 통합 테스트")
class UserSaveRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var userSaveRepository: UserSaveRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    

    private lateinit var testUserId: UUID
    private lateinit var testContentId: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트 데이터 준비
        val user = userRepository.save(
            me.onetwo.upvy.domain.user.model.User(
                email = "testuser@test.com",
                role = UserRole.USER
            )
        ).block()!!
        testUserId = user.id!!

        // 콘텐츠 생성
        testContentId = UUID.randomUUID()
        insertContent(testContentId, testUserId, "Test Content")
    }

    @AfterEach
    fun tearDown() {
        Mono.from(dslContext.deleteFrom(USER_SAVES)).block()
        Mono.from(dslContext.deleteFrom(CONTENT_PHOTOS)).block()
        Mono.from(dslContext.deleteFrom(CONTENTS)).block()
        Mono.from(dslContext.deleteFrom(USER_PROFILES)).block()
        Mono.from(dslContext.deleteFrom(USERS)).block()
    }

    @Nested
    @DisplayName("save - 저장 생성")
    inner class Save {

        @Test
        @DisplayName("저장을 생성하면, user_saves 테이블에 레코드가 저장된다")
        fun save_CreatesUserSave() {
            // Given: 준비된 사용자와 콘텐츠

            // When: 저장 생성
            val userSave = userSaveRepository.save(testUserId, testContentId).block()!!

            // Then: 생성된 저장 검증
            assertEquals(testUserId, userSave.userId)
            assertEquals(testContentId, userSave.contentId)
            assertEquals(testUserId.toString(), userSave.createdBy)
            assertEquals(testUserId.toString(), userSave.updatedBy)
        }

        @Test
        @DisplayName("이미 저장이 존재하면, 중복 생성 시 예외가 발생한다")
        fun save_WhenAlreadyExists_ThrowsException() {
            // Given: 이미 저장이 존재
            userSaveRepository.save(testUserId, testContentId).block()

            // When & Then: 중복 생성 시 예외 발생
            try {
                userSaveRepository.save(testUserId, testContentId).block()
                assert(false) { "Expected exception but none was thrown" }
            } catch (e: Exception) {
                // 예외 발생 확인 (duplicate, unique constraint violation 등)
                val message = e.message?.lowercase() ?: ""
                assertTrue(
                    message.contains("duplicate") ||
                    message.contains("unique") ||
                    message.contains("constraint"),
                    "Expected constraint violation but got: ${e.message}"
                )
            }
        }
    }

    @Nested
    @DisplayName("delete - 저장 삭제")
    inner class Delete {

        @Test
        @DisplayName("저장을 삭제하면, deleted_at이 설정된다")
        fun delete_SetDeletedAt() {
            // Given: 저장이 존재
            userSaveRepository.save(testUserId, testContentId).block()

            // When: 저장 삭제
            userSaveRepository.delete(testUserId, testContentId).block()

            // Then: deleted_at이 설정됨
            val exists = userSaveRepository.exists(testUserId, testContentId).block()!!
            assertFalse(exists)
        }

        @Test
        @DisplayName("존재하지 않는 저장을 삭제해도, 예외가 발생하지 않는다")
        fun delete_WhenNotExists_DoesNotThrow() {
            // Given: 저장이 존재하지 않음

            // When & Then: 삭제해도 예외 없음
            userSaveRepository.delete(testUserId, testContentId).block()
        }
    }

    @Nested
    @DisplayName("exists - 저장 존재 여부 확인")
    inner class Exists {

        @Test
        @DisplayName("저장이 존재하면, true를 반환한다")
        fun exists_WhenExists_ReturnsTrue() {
            // Given: 저장이 존재
            userSaveRepository.save(testUserId, testContentId).block()

            // When: 존재 여부 확인
            val exists = userSaveRepository.exists(testUserId, testContentId).block()!!

            // Then: true 반환
            assertTrue(exists)
        }

        @Test
        @DisplayName("저장이 존재하지 않으면, false를 반환한다")
        fun exists_WhenNotExists_ReturnsFalse() {
            // Given: 저장이 존재하지 않음

            // When: 존재 여부 확인
            val exists = userSaveRepository.exists(testUserId, testContentId).block()!!

            // Then: false 반환
            assertFalse(exists)
        }

        @Test
        @DisplayName("삭제된 저장은, false를 반환한다")
        fun exists_WhenDeleted_ReturnsFalse() {
            // Given: 저장이 삭제됨
            userSaveRepository.save(testUserId, testContentId).block()
            userSaveRepository.delete(testUserId, testContentId).block()

            // When: 존재 여부 확인
            val exists = userSaveRepository.exists(testUserId, testContentId).block()!!

            // Then: false 반환
            assertFalse(exists)
        }
    }

    @Nested
    @DisplayName("findByUserId - 사용자의 저장 목록 조회")
    inner class FindByUserId {

        @Test
        @DisplayName("사용자의 저장을 조회하면, 생성일시 내림차순으로 반환한다")
        fun findByUserId_ReturnsOrderedByCreatedAtDesc() {
            // Given: 2개의 콘텐츠를 저장
            val contentId1 = UUID.randomUUID()
            val contentId2 = UUID.randomUUID()
            insertContent(contentId1, testUserId, "Content 1")
            insertContent(contentId2, testUserId, "Content 2")

            userSaveRepository.save(testUserId, contentId1).block()
            // created_at은 millisecond 단위이므로 시간 차이 자동 보장
            userSaveRepository.save(testUserId, contentId2).block()

            // When: 저장 목록 조회
            val userSaves = userSaveRepository.findByUserId(testUserId).collectList().block()!!

            // Then: 최신순으로 반환
            assertEquals(2, userSaves.size)
            assertEquals(contentId2, userSaves[0].contentId) // 최신
            assertEquals(contentId1, userSaves[1].contentId) // 이전
        }

        @Test
        @DisplayName("삭제된 저장은 조회되지 않는다")
        fun findByUserId_ExcludesDeleted() {
            // Given: 저장 후 삭제
            userSaveRepository.save(testUserId, testContentId).block()
            userSaveRepository.delete(testUserId, testContentId).block()

            // When: 저장 목록 조회
            val userSaves = userSaveRepository.findByUserId(testUserId).collectList().block()!!

            // Then: 삭제된 저장은 조회되지 않음
            assertEquals(0, userSaves.size)
        }

        @Test
        @DisplayName("저장이 없으면, 빈 목록을 반환한다")
        fun findByUserId_WhenEmpty_ReturnsEmptyList() {
            // Given: 저장이 없음

            // When: 저장 목록 조회
            val userSaves = userSaveRepository.findByUserId(testUserId).collectList().block()!!

            // Then: 빈 목록 반환
            assertEquals(0, userSaves.size)
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
            .set(CONTENT_METADATA.CATEGORY, "TEST")
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
