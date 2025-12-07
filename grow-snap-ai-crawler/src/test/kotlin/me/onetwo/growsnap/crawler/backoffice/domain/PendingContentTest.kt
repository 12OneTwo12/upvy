package me.onetwo.growsnap.crawler.backoffice.domain

import me.onetwo.growsnap.crawler.domain.Difficulty
import me.onetwo.growsnap.crawler.domain.ReviewPriority
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("PendingContent 엔티티 테스트")
class PendingContentTest {

    @Nested
    @DisplayName("approve - 콘텐츠 승인")
    inner class ApproveTest {

        @Test
        @DisplayName("콘텐츠를 승인하면 상태와 검토 정보가 업데이트된다")
        fun approve_UpdatesStatusAndReviewInfo() {
            // Given
            val content = createTestContent()
            val originalUpdatedAt = content.updatedAt

            // When
            Thread.sleep(10) // 시간 차이 보장
            content.approve("reviewer", "published-123")

            // Then
            assertThat(content.status).isEqualTo(PendingContentStatus.APPROVED)
            assertThat(content.reviewedBy).isEqualTo("reviewer")
            assertThat(content.reviewedAt).isNotNull()
            assertThat(content.publishedContentId).isEqualTo("published-123")
            assertThat(content.updatedBy).isEqualTo("reviewer")
            assertThat(content.updatedAt).isAfter(originalUpdatedAt)
        }
    }

    @Nested
    @DisplayName("reject - 콘텐츠 거절")
    inner class RejectTest {

        @Test
        @DisplayName("콘텐츠를 거절하면 상태와 거절 사유가 저장된다")
        fun reject_UpdatesStatusAndReason() {
            // Given
            val content = createTestContent()

            // When
            content.reject("reviewer", "Quality is below standards")

            // Then
            assertThat(content.status).isEqualTo(PendingContentStatus.REJECTED)
            assertThat(content.reviewedBy).isEqualTo("reviewer")
            assertThat(content.reviewedAt).isNotNull()
            assertThat(content.rejectionReason).isEqualTo("Quality is below standards")
            assertThat(content.updatedBy).isEqualTo("reviewer")
        }
    }

    @Nested
    @DisplayName("updateMetadata - 메타데이터 수정")
    inner class UpdateMetadataTest {

        @Test
        @DisplayName("메타데이터를 수정하면 필드들이 업데이트된다")
        fun updateMetadata_UpdatesFields() {
            // Given
            val content = createTestContent()

            // When
            content.updateMetadata(
                title = "New Title",
                description = "New Description",
                category = Category.SCIENCE,
                difficulty = Difficulty.ADVANCED,
                tags = listOf("science", "physics"),
                updatedBy = "editor"
            )

            // Then
            assertThat(content.title).isEqualTo("New Title")
            assertThat(content.description).isEqualTo("New Description")
            assertThat(content.category).isEqualTo(Category.SCIENCE)
            assertThat(content.difficulty).isEqualTo(Difficulty.ADVANCED)
            assertThat(content.tags).isEqualTo("[\"science\", \"physics\"]")
            assertThat(content.updatedBy).isEqualTo("editor")
        }

        @Test
        @DisplayName("빈 태그 리스트는 null로 저장된다")
        fun updateMetadata_EmptyTags_SavesNull() {
            // Given
            val content = createTestContent()

            // When
            content.updateMetadata(
                title = "Title",
                description = null,
                category = Category.OTHER,
                difficulty = null,
                tags = emptyList(),
                updatedBy = "editor"
            )

            // Then
            assertThat(content.tags).isNull()
        }
    }

    @Nested
    @DisplayName("getTagsList - 태그 리스트 변환")
    inner class GetTagsListTest {

        @Test
        @DisplayName("JSON 형식의 태그를 리스트로 변환한다")
        fun getTagsList_ConvertsJsonToList() {
            // Given
            val content = createTestContent().apply {
                tags = "[\"kotlin\", \"spring\", \"jpa\"]"
            }

            // When
            val tags = content.getTagsList()

            // Then
            assertThat(tags).containsExactly("kotlin", "spring", "jpa")
        }

        @Test
        @DisplayName("태그가 null이면 빈 리스트를 반환한다")
        fun getTagsList_NullTags_ReturnsEmptyList() {
            // Given
            val content = createTestContent().apply {
                tags = null
            }

            // When
            val tags = content.getTagsList()

            // Then
            assertThat(tags).isEmpty()
        }

        @Test
        @DisplayName("태그가 빈 문자열이면 빈 리스트를 반환한다")
        fun getTagsList_EmptyTags_ReturnsEmptyList() {
            // Given
            val content = createTestContent().apply {
                tags = ""
            }

            // When
            val tags = content.getTagsList()

            // Then
            assertThat(tags).isEmpty()
        }
    }

    @Nested
    @DisplayName("Category.fromString - 카테고리 문자열 변환")
    inner class CategoryFromStringTest {

        @Test
        @DisplayName("유효한 카테고리 문자열을 변환한다")
        fun fromString_ValidCategory() {
            assertThat(Category.fromString("PROGRAMMING")).isEqualTo(Category.PROGRAMMING)
            assertThat(Category.fromString("programming")).isEqualTo(Category.PROGRAMMING)
            assertThat(Category.fromString("Science")).isEqualTo(Category.SCIENCE)
        }

        @Test
        @DisplayName("유효하지 않은 카테고리는 OTHER를 반환한다")
        fun fromString_InvalidCategory_ReturnsOther() {
            assertThat(Category.fromString("INVALID")).isEqualTo(Category.OTHER)
            assertThat(Category.fromString("unknown")).isEqualTo(Category.OTHER)
        }

        @Test
        @DisplayName("null은 OTHER를 반환한다")
        fun fromString_Null_ReturnsOther() {
            assertThat(Category.fromString(null)).isEqualTo(Category.OTHER)
        }
    }

    private fun createTestContent(): PendingContent {
        return PendingContent(
            id = 1L,
            aiContentJobId = 100L,
            title = "Original Title",
            description = "Original Description",
            category = Category.PROGRAMMING,
            difficulty = Difficulty.BEGINNER,
            tags = "[\"kotlin\"]",
            videoS3Key = "videos/test.mp4",
            qualityScore = 85,
            reviewPriority = ReviewPriority.HIGH,
            createdAt = Instant.now()
        )
    }
}
