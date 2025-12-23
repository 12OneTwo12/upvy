package me.onetwo.upvy.domain.tag.repository

import me.onetwo.upvy.domain.tag.model.Tag
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

/**
 * TagRepository 통합 테스트
 *
 * 실제 H2 데이터베이스를 사용하여 Repository 계층을 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("TagRepository 통합 테스트")
class TagRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var tagRepository: TagRepository

    @Nested
    @DisplayName("save - 태그 저장")
    inner class Save {

        @Test
        @DisplayName("태그를 저장하면, Auto Increment ID가 부여된다")
        fun save_WithValidTag_ReturnsTagWithId() {
            // Given: 새로운 태그
            val tag = Tag(
                name = "프로그래밍",
                normalizedName = "프로그래밍",
                usageCount = 0,
                createdBy = "test-user"
            )

            // When: 저장
            val savedTag = tagRepository.save(tag).block()!!

            // Then: ID가 부여됨
            assertThat(savedTag.id).isNotNull()
            assertThat(savedTag.name).isEqualTo("프로그래밍")
            assertThat(savedTag.normalizedName).isEqualTo("프로그래밍")
            assertThat(savedTag.usageCount).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("findByName - 이름으로 조회")
    inner class FindByName {

        @Test
        @DisplayName("존재하는 태그 이름으로 조회하면, 태그를 반환한다")
        fun findByName_WhenTagExists_ReturnsTag() {
            // Given: 태그 저장
            val savedTag = tagRepository.save(
                Tag(
                    name = "Java",
                    normalizedName = "java",
                    usageCount = 0,
                    createdBy = "test-user"
                )
            ).block()!!

            // When: 이름으로 조회
            val foundTag = tagRepository.findByName("Java").block()

            // Then: 태그가 조회됨
            assertThat(foundTag).isNotNull
            assertThat(foundTag!!.id).isEqualTo(savedTag.id)
            assertThat(foundTag.name).isEqualTo("Java")
        }

        @Test
        @DisplayName("존재하지 않는 태그 이름으로 조회하면, 빈 Mono를 반환한다")
        fun findByName_WhenTagNotExists_ReturnsEmpty() {
            // When: 존재하지 않는 이름으로 조회
            val foundTag = tagRepository.findByName("NonExistentTag").block()

            // Then: null 반환
            assertThat(foundTag).isNull()
        }
    }

    @Nested
    @DisplayName("findByNormalizedName - 정규화된 이름으로 조회")
    inner class FindByNormalizedName {

        @Test
        @DisplayName("정규화된 이름으로 조회하면, 태그를 반환한다")
        fun findByNormalizedName_WhenTagExists_ReturnsTag() {
            // Given: 태그 저장
            tagRepository.save(
                Tag(
                    name = "JavaScript",
                    normalizedName = "javascript",
                    usageCount = 0,
                    createdBy = "test-user"
                )
            ).block()!!

            // When: 정규화된 이름으로 조회
            val foundTag = tagRepository.findByNormalizedName("javascript").block()

            // Then: 태그가 조회됨
            assertThat(foundTag).isNotNull
            assertThat(foundTag!!.name).isEqualTo("JavaScript")
            assertThat(foundTag.normalizedName).isEqualTo("javascript")
        }
    }

    @Nested
    @DisplayName("findPopularTags - 인기 태그 조회")
    inner class FindPopularTags {

        @BeforeEach
        fun setUp() {
            // Given: 여러 태그를 usage_count 다르게 저장
            tagRepository.save(
                Tag(name = "Tag1", normalizedName = "tag1", usageCount = 100, createdBy = "test")
            ).block()
            tagRepository.save(
                Tag(name = "Tag2", normalizedName = "tag2", usageCount = 50, createdBy = "test")
            ).block()
            tagRepository.save(
                Tag(name = "Tag3", normalizedName = "tag3", usageCount = 200, createdBy = "test")
            ).block()
        }

        @Test
        @DisplayName("usage_count 기준 내림차순으로 정렬된 태그를 반환한다")
        fun findPopularTags_ReturnsTagsSortedByUsageCountDesc() {
            // When: 인기 태그 조회 (limit=2)
            val popularTags = tagRepository.findPopularTags(2).collectList().block()!!

            // Then: usage_count 높은 순으로 2개 반환
            assertThat(popularTags).hasSize(2)
            assertThat(popularTags[0].name).isEqualTo("Tag3")
            assertThat(popularTags[0].usageCount).isEqualTo(200)
            assertThat(popularTags[1].name).isEqualTo("Tag1")
            assertThat(popularTags[1].usageCount).isEqualTo(100)
        }
    }

    @Nested
    @DisplayName("incrementUsageCount - 사용 횟수 증가")
    inner class IncrementUsageCount {

        @Test
        @DisplayName("사용 횟수를 1 증가시킨다")
        fun incrementUsageCount_IncreasesCountByOne() {
            // Given: 태그 저장
            val savedTag = tagRepository.save(
                Tag(
                    name = "Kotlin",
                    normalizedName = "kotlin",
                    usageCount = 5,
                    createdBy = "test-user"
                )
            ).block()!!

            // When: 사용 횟수 증가
            val success = tagRepository.incrementUsageCount(savedTag.id!!).block()!!

            // Then: 증가 성공, usage_count = 6
            assertThat(success).isTrue()
            val updatedTag = tagRepository.findById(savedTag.id!!).block()!!
            assertThat(updatedTag.usageCount).isEqualTo(6)
        }
    }

    @Nested
    @DisplayName("decrementUsageCount - 사용 횟수 감소")
    inner class DecrementUsageCount {

        @Test
        @DisplayName("사용 횟수를 1 감소시킨다")
        fun decrementUsageCount_DecreasesCountByOne() {
            // Given: 태그 저장
            val savedTag = tagRepository.save(
                Tag(
                    name = "Python",
                    normalizedName = "python",
                    usageCount = 10,
                    createdBy = "test-user"
                )
            ).block()!!

            // When: 사용 횟수 감소
            val success = tagRepository.decrementUsageCount(savedTag.id!!).block()!!

            // Then: 감소 성공, usage_count = 9
            assertThat(success).isTrue()
            val updatedTag = tagRepository.findById(savedTag.id!!).block()!!
            assertThat(updatedTag.usageCount).isEqualTo(9)
        }

        @Test
        @DisplayName("usage_count가 0일 때는 감소하지 않는다")
        fun decrementUsageCount_WhenCountIsZero_DoesNotDecrease() {
            // Given: usage_count = 0인 태그
            val savedTag = tagRepository.save(
                Tag(
                    name = "Tag",
                    normalizedName = "tag",
                    usageCount = 0,
                    createdBy = "test-user"
                )
            ).block()!!

            // When: 사용 횟수 감소 시도
            val success = tagRepository.decrementUsageCount(savedTag.id!!).block()!!

            // Then: 감소 실패, usage_count 유지
            assertThat(success).isFalse()
            val updatedTag = tagRepository.findById(savedTag.id!!).block()!!
            assertThat(updatedTag.usageCount).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("delete - 태그 삭제 (Soft Delete)")
    inner class Delete {

        @Test
        @DisplayName("태그를 삭제하면, deleted_at이 설정된다")
        fun delete_WithValidTagId_SetsDeletedAt() {
            // Given: 태그 저장
            val savedTag = tagRepository.save(
                Tag(
                    name = "ToDelete",
                    normalizedName = "todelete",
                    usageCount = 0,
                    createdBy = "test-user"
                )
            ).block()!!

            // When: 삭제
            val success = tagRepository.delete(savedTag.id!!, "delete-user").block()!!

            // Then: 삭제 성공, findById로 조회 불가
            assertThat(success).isTrue()
            val deletedTag = tagRepository.findById(savedTag.id!!).block()
            assertThat(deletedTag).isNull()
        }
    }
}
