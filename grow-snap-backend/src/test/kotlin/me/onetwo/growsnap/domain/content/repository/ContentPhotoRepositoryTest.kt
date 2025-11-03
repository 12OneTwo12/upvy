package me.onetwo.growsnap.domain.content.repository

import me.onetwo.growsnap.domain.content.model.ContentPhoto
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENTS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_PHOTOS
import me.onetwo.growsnap.jooq.generated.tables.references.USER_PROFILES
import me.onetwo.growsnap.jooq.generated.tables.references.USERS
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
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
@DisplayName("콘텐츠 사진 Repository 통합 테스트")
class ContentPhotoRepositoryTest {

    @Autowired
    private lateinit var contentPhotoRepository: ContentPhotoRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    private lateinit var testUser: User
    private lateinit var testContentId: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트 사용자 및 콘텐츠 생성
        testUser = userRepository.save(
            User(
                email = "creator@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "creator-123",
                role = UserRole.USER
            )
        ).block()!!

        testContentId = UUID.randomUUID()
        insertContent(testContentId, testUser.id!!, "Photo Test")
    }

    @AfterEach
    fun tearDown() {
        Mono.from(dslContext.deleteFrom(CONTENT_PHOTOS)).block()
        Mono.from(dslContext.deleteFrom(CONTENTS)).block()
        Mono.from(dslContext.deleteFrom(USER_PROFILES)).block()
        Mono.from(dslContext.deleteFrom(USERS)).block()
    }

    @Nested
    @DisplayName("save - 사진 저장")
    inner class SavePhoto {

        @Test
        @DisplayName("유효한 사진을 저장하면, 데이터베이스에 저장된다")
        fun save_WithValidPhoto_SavesSuccessfully() {
            // Given: 테스트 사진
            val photo = ContentPhoto(
                contentId = testContentId,
                photoUrl = "https://s3.amazonaws.com/photo1.jpg",
                displayOrder = 0,
                width = 1080,
                height = 1080,
                createdAt = LocalDateTime.now(),
                createdBy = testUser.id!!.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUser.id!!.toString()
            )

            // When: 저장
            val result = contentPhotoRepository.save(photo)

            // Then: 저장 성공
            assertThat(result).isTrue

            // 데이터베이스에서 직접 확인
            val dbPhoto = dslContext.selectFrom(CONTENT_PHOTOS)
                .where(CONTENT_PHOTOS.CONTENT_ID.eq(testContentId.toString()))
                .and(CONTENT_PHOTOS.DELETED_AT.isNull)
                .fetchOne()

            assertThat(dbPhoto).isNotNull
            assertThat(dbPhoto!!.get(CONTENT_PHOTOS.PHOTO_URL)).isEqualTo("https://s3.amazonaws.com/photo1.jpg")
            assertThat(dbPhoto.get(CONTENT_PHOTOS.DISPLAY_ORDER)).isEqualTo(0)
        }

        @Test
        @DisplayName("여러 장의 사진을 순서대로 저장하면, 모두 저장된다")
        fun save_MultiplePhotos_SavesAllInOrder() {
            // Given: 3장의 사진
            val photo1 = ContentPhoto(
                contentId = testContentId,
                photoUrl = "https://s3.amazonaws.com/photo1.jpg",
                displayOrder = 0,
                width = 1080,
                height = 1080,
                createdAt = LocalDateTime.now(),
                createdBy = testUser.id!!.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUser.id!!.toString()
            )
            val photo2 = ContentPhoto(
                contentId = testContentId,
                photoUrl = "https://s3.amazonaws.com/photo2.jpg",
                displayOrder = 1,
                width = 1080,
                height = 1080,
                createdAt = LocalDateTime.now(),
                createdBy = testUser.id!!.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUser.id!!.toString()
            )
            val photo3 = ContentPhoto(
                contentId = testContentId,
                photoUrl = "https://s3.amazonaws.com/photo3.jpg",
                displayOrder = 2,
                width = 1080,
                height = 1080,
                createdAt = LocalDateTime.now(),
                createdBy = testUser.id!!.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUser.id!!.toString()
            )

            // When: 저장
            contentPhotoRepository.save(photo1)
            contentPhotoRepository.save(photo2)
            contentPhotoRepository.save(photo3)

            // Then: 3개 모두 저장됨
            val dbPhotos = dslContext.selectFrom(CONTENT_PHOTOS)
                .where(CONTENT_PHOTOS.CONTENT_ID.eq(testContentId.toString()))
                .and(CONTENT_PHOTOS.DELETED_AT.isNull)
                .orderBy(CONTENT_PHOTOS.DISPLAY_ORDER.asc())
                .fetch()

            assertThat(dbPhotos).hasSize(3)
            assertThat(dbPhotos[0].get(CONTENT_PHOTOS.DISPLAY_ORDER)).isEqualTo(0)
            assertThat(dbPhotos[1].get(CONTENT_PHOTOS.DISPLAY_ORDER)).isEqualTo(1)
            assertThat(dbPhotos[2].get(CONTENT_PHOTOS.DISPLAY_ORDER)).isEqualTo(2)
        }

        @Test
        @DisplayName("Audit Trail 필드가 자동으로 설정된다")
        fun save_WithValidPhoto_SetsAuditTrailFields() {
            // Given: 테스트 사진
            val now = LocalDateTime.now()
            val photo = ContentPhoto(
                contentId = testContentId,
                photoUrl = "https://s3.amazonaws.com/photo1.jpg",
                displayOrder = 0,
                width = 1080,
                height = 1080,
                createdAt = now,
                createdBy = testUser.id!!.toString(),
                updatedAt = now,
                updatedBy = testUser.id!!.toString()
            )

            // When: 저장
            contentPhotoRepository.save(photo)

            // Then: Audit Trail 확인
            val dbPhoto = dslContext.selectFrom(CONTENT_PHOTOS)
                .where(CONTENT_PHOTOS.CONTENT_ID.eq(testContentId.toString()))
                .and(CONTENT_PHOTOS.DELETED_AT.isNull)
                .fetchOne()

            assertThat(dbPhoto).isNotNull
            assertThat(dbPhoto!!.get(CONTENT_PHOTOS.CREATED_AT)).isNotNull
            assertThat(dbPhoto.get(CONTENT_PHOTOS.CREATED_BY)).isEqualTo(testUser.id!!.toString())
            assertThat(dbPhoto.get(CONTENT_PHOTOS.UPDATED_AT)).isNotNull
            assertThat(dbPhoto.get(CONTENT_PHOTOS.UPDATED_BY)).isEqualTo(testUser.id!!.toString())
            assertThat(dbPhoto.get(CONTENT_PHOTOS.DELETED_AT)).isNull()
        }
    }

    @Nested
    @DisplayName("findByContentId - 콘텐츠 ID로 사진 목록 조회")
    inner class FindByContentId {

        @Test
        @DisplayName("저장된 사진을 조회하면, display_order 순으로 반환된다")
        fun findByContentId_ExistingPhotos_ReturnsInDisplayOrder() {
            // Given: 3장의 사진을 역순으로 저장
            insertPhoto(testContentId, testUser.id!!, "photo3.jpg", 2)
            insertPhoto(testContentId, testUser.id!!, "photo1.jpg", 0)
            insertPhoto(testContentId, testUser.id!!, "photo2.jpg", 1)

            // When: 조회
            val photos = contentPhotoRepository.findByContentId(testContentId)

            // Then: display_order 순으로 정렬되어 반환
            assertThat(photos).hasSize(3)
            assertThat(photos[0].displayOrder).isEqualTo(0)
            assertThat(photos[0].photoUrl).contains("photo1.jpg")
            assertThat(photos[1].displayOrder).isEqualTo(1)
            assertThat(photos[1].photoUrl).contains("photo2.jpg")
            assertThat(photos[2].displayOrder).isEqualTo(2)
            assertThat(photos[2].photoUrl).contains("photo3.jpg")
        }

        @Test
        @DisplayName("사진이 없으면, 빈 목록이 반환된다")
        fun findByContentId_NoPhotos_ReturnsEmptyList() {
            // Given: 사진이 없는 콘텐츠
            val emptyContentId = UUID.randomUUID()
            insertContent(emptyContentId, testUser.id!!, "Empty Content")

            // When: 조회
            val photos = contentPhotoRepository.findByContentId(emptyContentId)

            // Then: 빈 목록
            assertThat(photos).isEmpty()
        }

        @Test
        @DisplayName("삭제된 사진은 조회되지 않는다 (Soft Delete)")
        fun findByContentId_DeletedPhotos_NotReturned() {
            // Given: 3장 저장, 1장 삭제
            insertPhoto(testContentId, testUser.id!!, "photo1.jpg", 0)
            insertPhoto(testContentId, testUser.id!!, "photo2.jpg", 1)
            insertPhoto(testContentId, testUser.id!!, "photo3.jpg", 2)

            // photo2를 Soft Delete
            dslContext.update(CONTENT_PHOTOS)
                .set(CONTENT_PHOTOS.DELETED_AT, LocalDateTime.now())
                .set(CONTENT_PHOTOS.UPDATED_BY, testUser.id!!.toString())
                .where(CONTENT_PHOTOS.CONTENT_ID.eq(testContentId.toString()))
                .and(CONTENT_PHOTOS.DISPLAY_ORDER.eq(1))
                .execute()

            // When: 조회
            val photos = contentPhotoRepository.findByContentId(testContentId)

            // Then: 2장만 조회 (삭제된 것 제외)
            assertThat(photos).hasSize(2)
            assertThat(photos.map { it.displayOrder }).containsExactly(0, 2)
        }
    }

    @Nested
    @DisplayName("deleteByContentId - 콘텐츠의 모든 사진 삭제 (Soft Delete)")
    inner class DeleteByContentId {

        @Test
        @DisplayName("콘텐츠의 모든 사진을 삭제하면, deleted_at이 설정된다")
        fun deleteByContentId_ExistingPhotos_SetsDeletedAt() {
            // Given: 3장의 사진 저장
            insertPhoto(testContentId, testUser.id!!, "photo1.jpg", 0)
            insertPhoto(testContentId, testUser.id!!, "photo2.jpg", 1)
            insertPhoto(testContentId, testUser.id!!, "photo3.jpg", 2)

            // When: 삭제
            val deletedCount = contentPhotoRepository.deleteByContentId(testContentId, testUser.id!!.toString())

            // Then: 3개 삭제됨
            assertThat(deletedCount).isEqualTo(3)

            // 데이터베이스에서 deleted_at 확인
            val dbPhotos = dslContext.selectFrom(CONTENT_PHOTOS)
                .where(CONTENT_PHOTOS.CONTENT_ID.eq(testContentId.toString()))
                .fetch()

            assertThat(dbPhotos).hasSize(3)
            dbPhotos.forEach { photo ->
                assertThat(photo.get(CONTENT_PHOTOS.DELETED_AT)).isNotNull
                assertThat(photo.get(CONTENT_PHOTOS.UPDATED_BY)).isEqualTo(testUser.id!!.toString())
            }

            // findByContentId로 조회 시 빈 목록 (Soft Delete)
            val photos = contentPhotoRepository.findByContentId(testContentId)
            assertThat(photos).isEmpty()
        }

        @Test
        @DisplayName("사진이 없는 콘텐츠 삭제 시, 0을 반환한다")
        fun deleteByContentId_NoPhotos_ReturnsZero() {
            // Given: 사진이 없는 콘텐츠
            val emptyContentId = UUID.randomUUID()
            insertContent(emptyContentId, testUser.id!!, "Empty Content")

            // When: 삭제
            val deletedCount = contentPhotoRepository.deleteByContentId(emptyContentId, testUser.id!!.toString())

            // Then: 0 반환
            assertThat(deletedCount).isEqualTo(0)
        }

        @Test
        @DisplayName("이미 삭제된 사진은 다시 삭제되지 않는다")
        fun deleteByContentId_AlreadyDeleted_NotDeletedAgain() {
            // Given: 3장 저장, 이미 모두 삭제됨
            insertPhoto(testContentId, testUser.id!!, "photo1.jpg", 0)
            insertPhoto(testContentId, testUser.id!!, "photo2.jpg", 1)
            insertPhoto(testContentId, testUser.id!!, "photo3.jpg", 2)
            contentPhotoRepository.deleteByContentId(testContentId, testUser.id!!.toString())

            // When: 다시 삭제 시도
            val deletedCount = contentPhotoRepository.deleteByContentId(testContentId, testUser.id!!.toString())

            // Then: 0 반환 (이미 삭제되었으므로)
            assertThat(deletedCount).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("findByContentIds - 여러 콘텐츠의 사진 목록 일괄 조회 (N+1 방지)")
    inner class FindByContentIds {

        @Test
        @DisplayName("여러 콘텐츠의 사진을 한 번에 조회하면, Map으로 그룹화되어 반환된다")
        fun findByContentIds_MultipleContents_ReturnsGroupedMap() {
            // Given: 2개의 콘텐츠와 각각의 사진들
            val content1 = testContentId
            val content2 = UUID.randomUUID()
            insertContent(content2, testUser.id!!, "Content 2")

            insertPhoto(content1, testUser.id!!, "content1_photo1.jpg", 0)
            insertPhoto(content1, testUser.id!!, "content1_photo2.jpg", 1)
            insertPhoto(content2, testUser.id!!, "content2_photo1.jpg", 0)
            insertPhoto(content2, testUser.id!!, "content2_photo2.jpg", 1)
            insertPhoto(content2, testUser.id!!, "content2_photo3.jpg", 2)

            // When: 일괄 조회
            val contentIds = listOf(content1, content2)
            val photosMap = contentPhotoRepository.findByContentIds(contentIds)

            // Then: Map으로 그룹화
            assertThat(photosMap).hasSize(2)
            assertThat(photosMap[content1]).hasSize(2)
            assertThat(photosMap[content2]).hasSize(3)

            // 각 콘텐츠의 사진이 display_order 순으로 정렬되어 있는지 확인
            assertThat(photosMap[content1]!![0].displayOrder).isEqualTo(0)
            assertThat(photosMap[content1]!![1].displayOrder).isEqualTo(1)
            assertThat(photosMap[content2]!![0].displayOrder).isEqualTo(0)
            assertThat(photosMap[content2]!![1].displayOrder).isEqualTo(1)
            assertThat(photosMap[content2]!![2].displayOrder).isEqualTo(2)
        }

        @Test
        @DisplayName("사진이 없는 콘텐츠는 Map에 포함되지 않는다")
        fun findByContentIds_NoPhotos_NotIncludedInMap() {
            // Given: 사진이 있는 콘텐츠 1개, 없는 콘텐츠 1개
            val content1 = testContentId
            val content2 = UUID.randomUUID()
            insertContent(content2, testUser.id!!, "Content 2 (empty)")

            insertPhoto(content1, testUser.id!!, "photo1.jpg", 0)

            // When: 일괄 조회
            val contentIds = listOf(content1, content2)
            val photosMap = contentPhotoRepository.findByContentIds(contentIds)

            // Then: 사진이 있는 콘텐츠만 Map에 포함
            assertThat(photosMap).hasSize(1)
            assertThat(photosMap[content1]).hasSize(1)
            assertThat(photosMap[content2]).isNull()
        }
    }

    /**
     * 콘텐츠 삽입 헬퍼 메서드
     */
    @Suppress("UnusedParameter")
    private fun insertContent(
        contentId: UUID,
        creatorId: UUID,
        title: String
    ) {
        val now = LocalDateTime.now()

        dslContext.insertInto(CONTENTS)
            .set(CONTENTS.ID, contentId.toString())
            .set(CONTENTS.CREATOR_ID, creatorId.toString())
            .set(CONTENTS.CONTENT_TYPE, ContentType.PHOTO.name)
            .set(CONTENTS.URL, "https://s3.amazonaws.com/default.jpg")
            .set(CONTENTS.THUMBNAIL_URL, "https://s3.amazonaws.com/thumb.jpg")
            .set(CONTENTS.WIDTH, 1080)
            .set(CONTENTS.HEIGHT, 1080)
            .set(CONTENTS.STATUS, ContentStatus.PUBLISHED.name)
            .set(CONTENTS.CREATED_AT, now)
            .set(CONTENTS.CREATED_BY, creatorId.toString())
            .set(CONTENTS.UPDATED_AT, now)
            .set(CONTENTS.UPDATED_BY, creatorId.toString())
            .execute()
    }

    /**
     * 사진 삽입 헬퍼 메서드
     */
    private fun insertPhoto(
        contentId: UUID,
        creatorId: UUID,
        photoUrl: String,
        displayOrder: Int
    ) {
        val now = LocalDateTime.now()

        dslContext.insertInto(CONTENT_PHOTOS)
            .set(CONTENT_PHOTOS.CONTENT_ID, contentId.toString())
            .set(CONTENT_PHOTOS.PHOTO_URL, "https://s3.amazonaws.com/$photoUrl")
            .set(CONTENT_PHOTOS.DISPLAY_ORDER, displayOrder)
            .set(CONTENT_PHOTOS.WIDTH, 1080)
            .set(CONTENT_PHOTOS.HEIGHT, 1080)
            .set(CONTENT_PHOTOS.CREATED_AT, now)
            .set(CONTENT_PHOTOS.CREATED_BY, creatorId.toString())
            .set(CONTENT_PHOTOS.UPDATED_AT, now)
            .set(CONTENT_PHOTOS.UPDATED_BY, creatorId.toString())
            .execute()
    }

    /**
     * 여러 콘텐츠의 사진 일괄 조회 헬퍼 메서드 (N+1 방지)
     */
    private fun findByContentIds(contentIds: List<UUID>): Map<UUID, List<ContentPhoto>> {
        return dslContext
            .select(
                CONTENT_PHOTOS.ID,
                CONTENT_PHOTOS.CONTENT_ID,
                CONTENT_PHOTOS.PHOTO_URL,
                CONTENT_PHOTOS.DISPLAY_ORDER,
                CONTENT_PHOTOS.WIDTH,
                CONTENT_PHOTOS.HEIGHT,
                CONTENT_PHOTOS.CREATED_AT,
                CONTENT_PHOTOS.CREATED_BY,
                CONTENT_PHOTOS.UPDATED_AT,
                CONTENT_PHOTOS.UPDATED_BY,
                CONTENT_PHOTOS.DELETED_AT
            )
            .from(CONTENT_PHOTOS)
            .where(CONTENT_PHOTOS.CONTENT_ID.`in`(contentIds.map { it.toString() }))
            .and(CONTENT_PHOTOS.DELETED_AT.isNull)
            .orderBy(CONTENT_PHOTOS.DISPLAY_ORDER.asc())
            .fetch()
            .groupBy { record -> UUID.fromString(record.getValue(CONTENT_PHOTOS.CONTENT_ID)) }
            .mapValues { (_, records) ->
                records.map { record ->
                    ContentPhoto(
                        id = record.getValue(CONTENT_PHOTOS.ID),
                        contentId = UUID.fromString(record.getValue(CONTENT_PHOTOS.CONTENT_ID)),
                        photoUrl = record.getValue(CONTENT_PHOTOS.PHOTO_URL)!!,
                        displayOrder = record.getValue(CONTENT_PHOTOS.DISPLAY_ORDER)!!,
                        width = record.getValue(CONTENT_PHOTOS.WIDTH)!!,
                        height = record.getValue(CONTENT_PHOTOS.HEIGHT)!!,
                        createdAt = record.getValue(CONTENT_PHOTOS.CREATED_AT)!!,
                        createdBy = record.getValue(CONTENT_PHOTOS.CREATED_BY),
                        updatedAt = record.getValue(CONTENT_PHOTOS.UPDATED_AT)!!,
                        updatedBy = record.getValue(CONTENT_PHOTOS.UPDATED_BY),
                        deletedAt = record.getValue(CONTENT_PHOTOS.DELETED_AT)
                    )
                }
            }
    }
}
