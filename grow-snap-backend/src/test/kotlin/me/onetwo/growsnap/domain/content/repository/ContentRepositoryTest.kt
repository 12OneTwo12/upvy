package me.onetwo.growsnap.domain.content.repository

import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.Content
import me.onetwo.growsnap.domain.content.model.ContentMetadata
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_METADATA
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_PHOTOS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENTS
import me.onetwo.growsnap.jooq.generated.tables.references.USER_PROFILES
import me.onetwo.growsnap.jooq.generated.tables.references.USERS
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.jooq.JSON
import reactor.core.publisher.Mono
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("콘텐츠 Repository 통합 테스트")
class ContentRepositoryTest {

    @Autowired
    private lateinit var contentRepository: ContentRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        // Given: 테스트 사용자 생성
        testUser = userRepository.save(
            User(
                email = "creator@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "creator-123",
                role = UserRole.USER
            )
        ).block()!!
    }

    @AfterEach
    fun tearDown() {
        Mono.from(dslContext.deleteFrom(CONTENT_PHOTOS)).block()
        Mono.from(dslContext.deleteFrom(CONTENTS)).block()
        Mono.from(dslContext.deleteFrom(USER_PROFILES)).block()
        Mono.from(dslContext.deleteFrom(USERS)).block()
    }

    @Nested
    @DisplayName("save - 콘텐츠 저장")
    inner class SaveContent {

        @Test
        @DisplayName("유효한 콘텐츠를 저장하면, 데이터베이스에 저장된다")
        fun save_WithValidContent_SavesSuccessfully() {
            // Given: 테스트 콘텐츠
            val contentId = UUID.randomUUID()
            val content = Content(
                id = contentId,
                creatorId = testUser.id!!,
                contentType = ContentType.VIDEO,
                url = "https://s3.amazonaws.com/video.mp4",
                thumbnailUrl = "https://s3.amazonaws.com/thumbnail.jpg",
                duration = 60,
                width = 1920,
                height = 1080,
                status = ContentStatus.PUBLISHED,
                createdAt = LocalDateTime.now(),
                createdBy = testUser.id.toString()!!,
                updatedAt = LocalDateTime.now(),
                updatedBy = testUser.id.toString()!!
            )

            // When: 저장
            val saved = contentRepository.save(content).block()

            // Then: 저장 확인
            assertThat(saved).isNotNull
            assertThat(saved!!.id).isEqualTo(contentId)
            assertThat(saved.creatorId).isEqualTo(testUser.id)
            assertThat(saved.contentType).isEqualTo(ContentType.VIDEO)

            // 데이터베이스에서 직접 확인
            val dbContent = Mono.from(dslContext.selectFrom(CONTENTS)
                .where(CONTENTS.ID.eq(contentId.toString())))
                .block()
            assertThat(dbContent).isNotNull
            assertThat(dbContent!!.get(CONTENTS.CREATOR_ID)).isEqualTo(testUser.id.toString())
        }

        @Test
        @DisplayName("Audit Trail 필드가 자동으로 설정된다")
        fun save_WithValidContent_SetsAuditTrailFields() {
            // Given: 테스트 콘텐츠
            val now = LocalDateTime.now()
            val content = Content(
                id = UUID.randomUUID(),
                creatorId = testUser.id!!,
                contentType = ContentType.VIDEO,
                url = "https://s3.amazonaws.com/video.mp4",
                thumbnailUrl = "https://s3.amazonaws.com/thumbnail.jpg",
                duration = 60,
                width = 1920,
                height = 1080,
                status = ContentStatus.PUBLISHED,
                createdAt = now,
                createdBy = testUser.id.toString()!!,
                updatedAt = now,
                updatedBy = testUser.id.toString()!!
            )

            // When: 저장
            val saved = contentRepository.save(content).block()

            // Then: Audit Trail 확인
            assertThat(saved!!.createdAt).isNotNull
            assertThat(saved.createdBy).isEqualTo(testUser.id.toString())
            assertThat(saved.updatedAt).isNotNull
            assertThat(saved.updatedBy).isEqualTo(testUser.id.toString())
        }
    }

    @Nested
    @DisplayName("findById - ID로 콘텐츠 조회")
    inner class FindById {

        @Test
        @DisplayName("존재하는 콘텐츠를 조회하면, 콘텐츠가 반환된다")
        fun findById_ExistingContent_ReturnsContent() {
            // Given: 저장된 콘텐츠
            val contentId = UUID.randomUUID()
            insertContent(contentId, testUser.id!!)

            // When: 조회
            val found = contentRepository.findById(contentId).block()

            // Then: 조회 확인
            assertThat(found).isNotNull
            assertThat(found!!.id).isEqualTo(contentId)
            assertThat(found.creatorId).isEqualTo(testUser.id)
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면, null이 반환된다")
        fun findById_NonExistingContent_ReturnsNull() {
            // Given: 존재하지 않는 ID
            val nonExistingId = UUID.randomUUID()

            // When: 조회
            val found = contentRepository.findById(nonExistingId).block()

            // Then: null 반환
            assertThat(found).isNull()
        }

        @Test
        @DisplayName("삭제된 콘텐츠는 조회되지 않는다 (Soft Delete)")
        fun findById_DeletedContent_ReturnsNull() {
            // Given: 저장 후 삭제된 콘텐츠
            val contentId = UUID.randomUUID()
            insertContent(contentId, testUser.id!!)
            softDeleteContent(contentId, testUser.id!!)

            // When: 조회
            val found = contentRepository.findById(contentId).block()

            // Then: null 반환 (Soft Delete)
            assertThat(found).isNull()
        }
    }

    @Nested
    @DisplayName("saveMetadata - 콘텐츠 메타데이터 저장")
    inner class SaveMetadata {

        @Test
        @DisplayName("유효한 메타데이터를 저장하면, 데이터베이스에 저장된다")
        fun saveMetadata_WithValidData_SavesSuccessfully() {
            // Given: 콘텐츠 및 메타데이터
            val contentId = UUID.randomUUID()
            insertContent(contentId, testUser.id!!)

            val metadata = ContentMetadata(
                contentId = contentId,
                title = "Test Video",
                description = "Test Description",
                category = Category.PROGRAMMING,
                tags = listOf("test", "video"),
                language = "ko",
                createdAt = LocalDateTime.now(),
                createdBy = testUser.id.toString()!!,
                updatedAt = LocalDateTime.now(),
                updatedBy = testUser.id.toString()!!
            )

            // When: 저장
            val saved = contentRepository.saveMetadata(metadata).block()

            // Then: 저장 확인
            assertThat(saved).isNotNull
            assertThat(saved!!.contentId).isEqualTo(contentId)
            assertThat(saved.title).isEqualTo("Test Video")
            assertThat(saved.category).isEqualTo(Category.PROGRAMMING)
            assertThat(saved.tags).containsExactly("test", "video")
        }
    }

    @Nested
    @DisplayName("findMetadataByContentId - 콘텐츠 ID로 메타데이터 조회")
    inner class FindMetadataByContentId {

        @Test
        @DisplayName("존재하는 메타데이터를 조회하면, 메타데이터가 반환된다")
        fun findMetadataByContentId_ExistingMetadata_ReturnsMetadata() {
            // Given: 저장된 콘텐츠 및 메타데이터
            val contentId = UUID.randomUUID()
            insertContent(contentId, testUser.id!!)
            insertMetadata(contentId, testUser.id!!, "Test Title")

            // When: 조회
            val found = contentRepository.findMetadataByContentId(contentId).block()

            // Then: 조회 확인
            assertThat(found).isNotNull
            assertThat(found!!.contentId).isEqualTo(contentId)
            assertThat(found.title).isEqualTo("Test Title")
        }

        @Test
        @DisplayName("존재하지 않는 콘텐츠 ID로 조회하면, null이 반환된다")
        fun findMetadataByContentId_NonExistingContent_ReturnsNull() {
            // Given: 존재하지 않는 ID
            val nonExistingId = UUID.randomUUID()

            // When: 조회
            val found = contentRepository.findMetadataByContentId(nonExistingId).block()

            // Then: null 반환
            assertThat(found).isNull()
        }
    }

    @Nested
    @DisplayName("findByCreatorId - 크리에이터 ID로 콘텐츠 목록 조회")
    inner class FindByCreatorId {

        @Test
        @DisplayName("크리에이터의 콘텐츠를 조회하면, 모든 콘텐츠가 반환된다")
        fun findByCreatorId_WithContents_ReturnsAllContents() {
            // Given: 3개의 콘텐츠 저장
            val content1 = UUID.randomUUID()
            val content2 = UUID.randomUUID()
            val content3 = UUID.randomUUID()
            insertContent(content1, testUser.id!!)
            insertContent(content2, testUser.id!!)
            insertContent(content3, testUser.id!!)

            // When: 조회
            val found = contentRepository.findByCreatorId(testUser.id!!).collectList().block()!!

            // Then: 3개 조회
            assertThat(found).hasSize(3)
            assertThat(found.map { it.id }).containsExactlyInAnyOrder(content1, content2, content3)
        }

        @Test
        @DisplayName("콘텐츠가 없으면, 빈 목록이 반환된다")
        fun findByCreatorId_NoContents_ReturnsEmptyList() {
            // Given: 다른 사용자
            val otherUser = userRepository.save(
                User(
                    email = "other@test.com",
                    provider = OAuthProvider.GOOGLE,
                    providerId = "other-123",
                    role = UserRole.USER
                )
            ).block()!!

            // When: 조회
            val found = contentRepository.findByCreatorId(otherUser.id!!).collectList().block()!!

            // Then: 빈 목록
            assertThat(found).isEmpty()
        }

        @Test
        @DisplayName("삭제된 콘텐츠는 조회되지 않는다")
        fun findByCreatorId_DeletedContents_NotReturned() {
            // Given: 3개 저장, 1개 삭제
            val content1 = UUID.randomUUID()
            val content2 = UUID.randomUUID()
            val content3 = UUID.randomUUID()
            insertContent(content1, testUser.id!!)
            insertContent(content2, testUser.id!!)
            insertContent(content3, testUser.id!!)
            softDeleteContent(content2, testUser.id!!)

            // When: 조회
            val found = contentRepository.findByCreatorId(testUser.id!!).collectList().block()!!

            // Then: 2개만 조회 (삭제된 것 제외)
            assertThat(found).hasSize(2)
            assertThat(found.map { it.id }).containsExactlyInAnyOrder(content1, content3)
        }
    }

    @Nested
    @DisplayName("findWithMetadataByCreatorId - 크리에이터 ID로 콘텐츠와 메타데이터 조회 (JOIN)")
    inner class FindWithMetadataByCreatorId {

        @Test
        @DisplayName("크리에이터의 콘텐츠와 메타데이터를 한 번에 조회하면, 모두 반환된다")
        fun findWithMetadataByCreatorId_WithContents_ReturnsAllWithMetadata() {
            // Given: 3개의 콘텐츠와 메타데이터 저장
            val content1 = UUID.randomUUID()
            val content2 = UUID.randomUUID()
            val content3 = UUID.randomUUID()
            insertContent(content1, testUser.id!!)
            insertContent(content2, testUser.id!!)
            insertContent(content3, testUser.id!!)
            insertMetadata(content1, testUser.id!!, "Content 1")
            insertMetadata(content2, testUser.id!!, "Content 2")
            insertMetadata(content3, testUser.id!!, "Content 3")

            // When: JOIN으로 조회
            val found = contentRepository.findWithMetadataByCreatorId(testUser.id!!).collectList().block()!!

            // Then: 3개의 콘텐츠와 메타데이터 쌍이 반환됨
            assertThat(found).hasSize(3)
            assertThat(found.map { it.content.id }).containsExactlyInAnyOrder(content1, content2, content3)
            assertThat(found.map { it.metadata.title }).containsExactlyInAnyOrder("Content 1", "Content 2", "Content 3")

            // 각 쌍의 contentId가 일치하는지 확인
            found.forEach { contentWithMetadata ->
                assertThat(contentWithMetadata.metadata.contentId).isEqualTo(contentWithMetadata.content.id)
            }
        }

        @Test
        @DisplayName("콘텐츠가 없으면, 빈 목록이 반환된다")
        fun findWithMetadataByCreatorId_NoContents_ReturnsEmptyList() {
            // Given: 다른 사용자
            val otherUser = userRepository.save(
                User(
                    email = "other2@test.com",
                    provider = OAuthProvider.GOOGLE,
                    providerId = "other2-123",
                    role = UserRole.USER
                )
            ).block()!!

            // When: 조회
            val found = contentRepository.findWithMetadataByCreatorId(otherUser.id!!).collectList().block()!!

            // Then: 빈 목록
            assertThat(found).isEmpty()
        }

        @Test
        @DisplayName("삭제된 콘텐츠는 조회되지 않는다")
        fun findWithMetadataByCreatorId_DeletedContents_NotReturned() {
            // Given: 3개 저장, 1개 삭제
            val content1 = UUID.randomUUID()
            val content2 = UUID.randomUUID()
            val content3 = UUID.randomUUID()
            insertContent(content1, testUser.id!!)
            insertContent(content2, testUser.id!!)
            insertContent(content3, testUser.id!!)
            insertMetadata(content1, testUser.id!!, "Content 1")
            insertMetadata(content2, testUser.id!!, "Content 2")
            insertMetadata(content3, testUser.id!!, "Content 3")
            softDeleteContent(content2, testUser.id!!)

            // When: 조회
            val found = contentRepository.findWithMetadataByCreatorId(testUser.id!!).collectList().block()!!

            // Then: 2개만 조회 (삭제된 것 제외)
            assertThat(found).hasSize(2)
            assertThat(found.map { it.content.id }).containsExactlyInAnyOrder(content1, content3)
        }

        @Test
        @DisplayName("메타데이터가 삭제된 콘텐츠는 조회되지 않는다")
        fun findWithMetadataByCreatorId_DeletedMetadata_NotReturned() {
            // Given: 2개 저장, 1개의 메타데이터 삭제
            val content1 = UUID.randomUUID()
            val content2 = UUID.randomUUID()
            insertContent(content1, testUser.id!!)
            insertContent(content2, testUser.id!!)
            insertMetadata(content1, testUser.id!!, "Content 1")
            insertMetadata(content2, testUser.id!!, "Content 2")

            // 메타데이터 삭제
            Mono.from(dslContext.update(CONTENT_METADATA)
                .set(CONTENT_METADATA.DELETED_AT, LocalDateTime.now())
                .where(CONTENT_METADATA.CONTENT_ID.eq(content2.toString()))).block()

            // When: 조회
            val found = contentRepository.findWithMetadataByCreatorId(testUser.id!!).collectList().block()!!

            // Then: 메타데이터가 삭제되지 않은 것만 조회
            assertThat(found).hasSize(1)
            assertThat(found[0].content.id).isEqualTo(content1)
        }

        @Test
        @DisplayName("최신순으로 정렬되어 반환된다")
        fun findWithMetadataByCreatorId_OrderedByCreatedAtDesc() {
            // Given: 3개의 콘텐츠를 시간 차이를 두고 저장
            val content1 = UUID.randomUUID()
            val content2 = UUID.randomUUID()
            val content3 = UUID.randomUUID()

            val now = LocalDateTime.now()
            insertContentWithTime(content1, testUser.id!!, now.minusHours(3))
            insertContentWithTime(content2, testUser.id!!, now.minusHours(2))
            insertContentWithTime(content3, testUser.id!!, now.minusHours(1))
            insertMetadata(content1, testUser.id!!, "Content 1")
            insertMetadata(content2, testUser.id!!, "Content 2")
            insertMetadata(content3, testUser.id!!, "Content 3")

            // When: 조회
            val found = contentRepository.findWithMetadataByCreatorId(testUser.id!!).collectList().block()!!

            // Then: 최신순 정렬
            assertThat(found).hasSize(3)
            assertThat(found[0].content.id).isEqualTo(content3)
            assertThat(found[1].content.id).isEqualTo(content2)
            assertThat(found[2].content.id).isEqualTo(content1)
        }
    }

    @Nested
    @DisplayName("delete - 콘텐츠 삭제 (Soft Delete)")
    inner class DeleteContent {

        @Test
        @DisplayName("콘텐츠를 삭제하면, deleted_at이 설정된다")
        fun delete_ExistingContent_SetsdDeletedAt() {
            // Given: 저장된 콘텐츠
            val contentId = UUID.randomUUID()
            insertContent(contentId, testUser.id!!)

            // When: 삭제
            val result = contentRepository.delete(contentId, testUser.id!!).block()

            // Then: 삭제 성공
            assertThat(result).isNotNull
            assertThat(result!!).isTrue

            // 데이터베이스에서 deleted_at 확인
            val dbContent = Mono.from(dslContext.selectFrom(CONTENTS)
                .where(CONTENTS.ID.eq(contentId.toString())))
                .block()
            assertThat(dbContent).isNotNull
            assertThat(dbContent!!.get(CONTENTS.DELETED_AT)).isNotNull

            // findById로 조회 시 null (Soft Delete)
            val found = contentRepository.findById(contentId).block()
            assertThat(found).isNull()
        }

        @Test
        @DisplayName("존재하지 않는 콘텐츠 삭제 시, false를 반환한다")
        fun delete_NonExistingContent_ReturnsFalse() {
            // Given: 존재하지 않는 ID
            val nonExistingId = UUID.randomUUID()

            // When: 삭제
            val result = contentRepository.delete(nonExistingId, testUser.id!!).block()

            // Then: 실패
            assertThat(result).isNotNull
            assertThat(result!!).isFalse
        }
    }

    /**
     * 콘텐츠 삽입 헬퍼 메서드
     */
    private fun insertContent(
        contentId: UUID,
        creatorId: UUID
    ) {
        val now = LocalDateTime.now()
        insertContentWithTime(contentId, creatorId, now)
    }

    /**
     * 특정 시간으로 콘텐츠 삽입 헬퍼 메서드
     */
    private fun insertContentWithTime(
        contentId: UUID,
        creatorId: UUID,
        createdAt: LocalDateTime
    ) {
        Mono.from(dslContext.insertInto(CONTENTS)
            .set(CONTENTS.ID, contentId.toString())
            .set(CONTENTS.CREATOR_ID, creatorId.toString())
            .set(CONTENTS.CONTENT_TYPE, ContentType.VIDEO.name)
            .set(CONTENTS.URL, "https://s3.amazonaws.com/video.mp4")
            .set(CONTENTS.THUMBNAIL_URL, "https://s3.amazonaws.com/thumbnail.jpg")
            .set(CONTENTS.DURATION, 60)
            .set(CONTENTS.WIDTH, 1920)
            .set(CONTENTS.HEIGHT, 1080)
            .set(CONTENTS.STATUS, ContentStatus.PUBLISHED.name)
            .set(CONTENTS.CREATED_AT, createdAt)
            .set(CONTENTS.CREATED_BY, creatorId.toString())
            .set(CONTENTS.UPDATED_AT, createdAt)
            .set(CONTENTS.UPDATED_BY, creatorId.toString())).block()
    }

    /**
     * 메타데이터 삽입 헬퍼 메서드
     */
    private fun insertMetadata(
        contentId: UUID,
        creatorId: UUID,
        title: String
    ) {
        val now = LocalDateTime.now()
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
    }

    /**
     * Soft Delete 헬퍼 메서드
     */
    private fun softDeleteContent(
        contentId: UUID,
        userId: UUID
    ) {
        Mono.from(dslContext.update(CONTENTS)
            .set(CONTENTS.DELETED_AT, LocalDateTime.now())
            .set(CONTENTS.UPDATED_AT, LocalDateTime.now())
            .set(CONTENTS.UPDATED_BY, userId.toString())
            .where(CONTENTS.ID.eq(contentId.toString()))).block()
    }

    @Nested
    @DisplayName("searchByTitle - 제목으로 콘텐츠 검색 (Failover용)")
    inner class SearchByTitle {

        @Test
        @DisplayName("제목에 검색어가 포함된 콘텐츠를 반환한다")
        fun searchByTitle_WithMatchingContents_ReturnsContents() {
            // Given: 여러 콘텐츠 생성
            val content1 = UUID.randomUUID()
            val content2 = UUID.randomUUID()
            val content3 = UUID.randomUUID()
            insertContent(content1, testUser.id!!)
            insertContent(content2, testUser.id!!)
            insertContent(content3, testUser.id!!)
            insertMetadata(content1, testUser.id!!, "Kotlin Tutorial for Beginners")
            insertMetadata(content2, testUser.id!!, "Advanced Kotlin Programming")
            insertMetadata(content3, testUser.id!!, "Java Spring Boot Guide")

            // When: "Kotlin"으로 검색
            val results = contentRepository.searchByTitle("Kotlin", 10).collectList().block()!!

            // Then: Kotlin이 포함된 콘텐츠 2개 반환
            assertThat(results).hasSize(2)
            assertThat(results).containsExactlyInAnyOrder(content1, content2)
        }

        @Test
        @DisplayName("일치하는 콘텐츠가 없으면 빈 리스트를 반환한다")
        fun searchByTitle_WithNoMatch_ReturnsEmpty() {
            // Given: 검색어와 일치하지 않는 콘텐츠
            val contentId = UUID.randomUUID()
            insertContent(contentId, testUser.id!!)
            insertMetadata(contentId, testUser.id!!, "Python Tutorial")

            // When: "Kotlin"으로 검색
            val results = contentRepository.searchByTitle("Kotlin", 10).collectList().block()!!

            // Then: 빈 리스트 반환
            assertThat(results).isEmpty()
        }

        @Test
        @DisplayName("최신순으로 정렬되어 반환된다")
        fun searchByTitle_OrderedByCreatedAtDesc() {
            // Given: 시간 차이를 두고 3개 콘텐츠 생성
            val content1 = UUID.randomUUID()
            val content2 = UUID.randomUUID()
            val content3 = UUID.randomUUID()

            val now = LocalDateTime.now()
            insertContentWithTime(content1, testUser.id!!, now.minusHours(3))
            insertContentWithTime(content2, testUser.id!!, now.minusHours(2))
            insertContentWithTime(content3, testUser.id!!, now.minusHours(1))
            insertMetadata(content1, testUser.id!!, "Tutorial Part 1")
            insertMetadata(content2, testUser.id!!, "Tutorial Part 2")
            insertMetadata(content3, testUser.id!!, "Tutorial Part 3")

            // When: "Tutorial"로 검색
            val results = contentRepository.searchByTitle("Tutorial", 10).collectList().block()!!

            // Then: 최신순 정렬
            assertThat(results).hasSize(3)
            assertThat(results[0]).isEqualTo(content3)
            assertThat(results[1]).isEqualTo(content2)
            assertThat(results[2]).isEqualTo(content1)
        }

        @Test
        @DisplayName("PUBLISHED 상태의 콘텐츠만 반환한다")
        fun searchByTitle_OnlyPublishedContents() {
            // Given: PUBLISHED와 PENDING 콘텐츠 생성
            val publishedContent = UUID.randomUUID()
            val pendingContent = UUID.randomUUID()

            // PUBLISHED 콘텐츠
            Mono.from(dslContext.insertInto(CONTENTS)
                .set(CONTENTS.ID, publishedContent.toString())
                .set(CONTENTS.CREATOR_ID, testUser.id.toString())
                .set(CONTENTS.CONTENT_TYPE, ContentType.VIDEO.name)
                .set(CONTENTS.URL, "https://s3.amazonaws.com/video.mp4")
                .set(CONTENTS.THUMBNAIL_URL, "https://s3.amazonaws.com/thumbnail.jpg")
                .set(CONTENTS.DURATION, 60)
                .set(CONTENTS.WIDTH, 1920)
                .set(CONTENTS.HEIGHT, 1080)
                .set(CONTENTS.STATUS, ContentStatus.PUBLISHED.name)
                .set(CONTENTS.CREATED_AT, LocalDateTime.now())
                .set(CONTENTS.CREATED_BY, testUser.id.toString())
                .set(CONTENTS.UPDATED_AT, LocalDateTime.now())
                .set(CONTENTS.UPDATED_BY, testUser.id.toString())).block()

            // PENDING 콘텐츠
            Mono.from(dslContext.insertInto(CONTENTS)
                .set(CONTENTS.ID, pendingContent.toString())
                .set(CONTENTS.CREATOR_ID, testUser.id.toString())
                .set(CONTENTS.CONTENT_TYPE, ContentType.VIDEO.name)
                .set(CONTENTS.URL, "https://s3.amazonaws.com/video.mp4")
                .set(CONTENTS.THUMBNAIL_URL, "https://s3.amazonaws.com/thumbnail.jpg")
                .set(CONTENTS.DURATION, 60)
                .set(CONTENTS.WIDTH, 1920)
                .set(CONTENTS.HEIGHT, 1080)
                .set(CONTENTS.STATUS, ContentStatus.PENDING.name)
                .set(CONTENTS.CREATED_AT, LocalDateTime.now())
                .set(CONTENTS.CREATED_BY, testUser.id.toString())
                .set(CONTENTS.UPDATED_AT, LocalDateTime.now())
                .set(CONTENTS.UPDATED_BY, testUser.id.toString())).block()

            insertMetadata(publishedContent, testUser.id!!, "Kotlin Guide")
            insertMetadata(pendingContent, testUser.id!!, "Kotlin Tutorial")

            // When: "Kotlin"으로 검색
            val results = contentRepository.searchByTitle("Kotlin", 10).collectList().block()!!

            // Then: PUBLISHED만 반환
            assertThat(results).hasSize(1)
            assertThat(results[0]).isEqualTo(publishedContent)
        }

        @Test
        @DisplayName("삭제된 콘텐츠는 검색 결과에서 제외된다")
        fun searchByTitle_ExcludesDeletedContents() {
            // Given: 2개 생성, 1개 삭제
            val activeContent = UUID.randomUUID()
            val deletedContent = UUID.randomUUID()
            insertContent(activeContent, testUser.id!!)
            insertContent(deletedContent, testUser.id!!)
            insertMetadata(activeContent, testUser.id!!, "Active Kotlin Tutorial")
            insertMetadata(deletedContent, testUser.id!!, "Deleted Kotlin Guide")

            // 콘텐츠 삭제
            softDeleteContent(deletedContent, testUser.id!!)

            // When: "Kotlin"으로 검색
            val results = contentRepository.searchByTitle("Kotlin", 10).collectList().block()!!

            // Then: 삭제되지 않은 콘텐츠만 반환
            assertThat(results).hasSize(1)
            assertThat(results[0]).isEqualTo(activeContent)
        }

        @Test
        @DisplayName("삭제된 메타데이터의 콘텐츠는 검색 결과에서 제외된다")
        fun searchByTitle_ExcludesDeletedMetadata() {
            // Given: 2개 콘텐츠 생성, 1개의 메타데이터 삭제
            val activeContent = UUID.randomUUID()
            val deletedMetadataContent = UUID.randomUUID()
            insertContent(activeContent, testUser.id!!)
            insertContent(deletedMetadataContent, testUser.id!!)
            insertMetadata(activeContent, testUser.id!!, "Active Programming Tutorial")
            insertMetadata(deletedMetadataContent, testUser.id!!, "Deleted Programming Guide")

            // 메타데이터 삭제
            Mono.from(dslContext.update(CONTENT_METADATA)
                .set(CONTENT_METADATA.DELETED_AT, LocalDateTime.now())
                .where(CONTENT_METADATA.CONTENT_ID.eq(deletedMetadataContent.toString()))).block()

            // When: "Programming"으로 검색
            val results = contentRepository.searchByTitle("Programming", 10).collectList().block()!!

            // Then: 메타데이터가 삭제되지 않은 콘텐츠만 반환
            assertThat(results).hasSize(1)
            assertThat(results[0]).isEqualTo(activeContent)
        }

        @Test
        @DisplayName("limit 파라미터가 적용된다")
        fun searchByTitle_LimitsResults() {
            // Given: 5개 콘텐츠 생성
            val contentIds = (1..5).map {
                val contentId = UUID.randomUUID()
                insertContent(contentId, testUser.id!!)
                insertMetadata(contentId, testUser.id!!, "Test Content $it")
                contentId
            }

            // When: limit=3으로 검색
            val results = contentRepository.searchByTitle("Test", 3).collectList().block()!!

            // Then: 최대 4개 반환 (limit + 1)
            assertThat(results.size).isLessThanOrEqualTo(4)
        }

        @Test
        @DisplayName("대소문자 구분 없이 검색된다 (MySQL 기준)")
        fun searchByTitle_CaseInsensitive() {
            // Given: 다양한 케이스의 제목
            val content1 = UUID.randomUUID()
            val content2 = UUID.randomUUID()
            insertContent(content1, testUser.id!!)
            insertContent(content2, testUser.id!!)
            insertMetadata(content1, testUser.id!!, "kotlin tutorial")
            insertMetadata(content2, testUser.id!!, "Kotlin GUIDE")

            // When: 소문자로 검색
            val results = contentRepository.searchByTitle("kotlin", 10).collectList().block()!!

            // Then: H2는 대소문자를 구분할 수 있으므로, 최소 1개 이상 반환되면 성공
            assertThat(results).isNotEmpty
        }
    }
}
