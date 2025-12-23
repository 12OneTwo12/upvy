package me.onetwo.upvy.domain.tag.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.upvy.domain.tag.model.ContentTag
import me.onetwo.upvy.domain.tag.model.Tag
import me.onetwo.upvy.domain.tag.repository.ContentTagRepository
import me.onetwo.upvy.domain.tag.repository.TagRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * TagService 단위 테스트
 *
 * MockK를 사용하여 Repository를 모킹하고 Service 비즈니스 로직을 테스트합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("TagService 단위 테스트")
class TagServiceImplTest {

    private lateinit var tagRepository: TagRepository
    private lateinit var contentTagRepository: ContentTagRepository
    private lateinit var tagService: TagService

    @BeforeEach
    fun setUp() {
        tagRepository = mockk()
        contentTagRepository = mockk()
        tagService = TagServiceImpl(tagRepository, contentTagRepository)
    }

    @Nested
    @DisplayName("normalizeTagName - 태그 이름 정규화")
    inner class NormalizeTagName {

        @Test
        @DisplayName("태그 이름을 소문자로 변환하고 # 제거한다")
        fun normalizeTagName_WithHashAndUppercase_ReturnsLowercase() {
            // Given: # 포함, 대문자 태그
            val tagName = "#Java"

            // When: 정규화
            val normalized = tagService.normalizeTagName(tagName)

            // Then: 소문자, # 제거
            assertThat(normalized).isEqualTo("java")
        }

        @Test
        @DisplayName("앞뒤 공백을 제거한다")
        fun normalizeTagName_WithWhitespace_TrimsWhitespace() {
            // Given: 공백 포함
            val tagName = "  Kotlin  "

            // When: 정규화
            val normalized = tagService.normalizeTagName(tagName)

            // Then: 공백 제거
            assertThat(normalized).isEqualTo("kotlin")
        }
    }

    @Nested
    @DisplayName("findOrCreateTag - 태그 찾거나 생성")
    inner class FindOrCreateTag {

        @Test
        @DisplayName("기존 태그가 존재하면, 기존 태그를 반환한다")
        fun findOrCreateTag_WhenTagExists_ReturnsExistingTag() {
            // Given: 기존 태그 존재
            val existingTag = Tag(
                id = 1L,
                name = "Java",
                normalizedName = "java",
                usageCount = 5
            )
            every { tagRepository.findByNormalizedName("java") } returns Mono.just(existingTag)

            // When: 태그 찾기
            val result = tagService.findOrCreateTag("Java", "test-user")

            // Then: 기존 태그 반환, save 호출 안 함
            StepVerifier.create(result)
                .assertNext { tag ->
                    assertThat(tag.id).isEqualTo(1L)
                    assertThat(tag.name).isEqualTo("Java")
                }
                .verifyComplete()

            verify(exactly = 1) { tagRepository.findByNormalizedName("java") }
            verify(exactly = 0) { tagRepository.save(any()) }
        }

        @Test
        @DisplayName("태그가 없으면, 새로운 태그를 생성한다")
        fun findOrCreateTag_WhenTagNotExists_CreatesNewTag() {
            // Given: 태그 없음
            val newTag = Tag(
                id = 1L,
                name = "Python",
                normalizedName = "python",
                usageCount = 0,
                createdBy = "test-user"
            )
            every { tagRepository.findByNormalizedName("python") } returns Mono.empty()
            every { tagRepository.save(any()) } returns Mono.just(newTag)

            // When: 태그 생성
            val result = tagService.findOrCreateTag("Python", "test-user")

            // Then: 새 태그 저장됨
            StepVerifier.create(result)
                .assertNext { tag ->
                    assertThat(tag.id).isEqualTo(1L)
                    assertThat(tag.name).isEqualTo("Python")
                }
                .verifyComplete()

            verify(exactly = 1) { tagRepository.findByNormalizedName("python") }
            verify(exactly = 1) { tagRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("attachTagsToContent - 콘텐츠에 태그 연결")
    inner class AttachTagsToContent {

        @Test
        @DisplayName("새로운 태그를 콘텐츠에 연결하면, usage_count가 증가한다")
        fun attachTagsToContent_WithNewTags_IncrementsUsageCount() {
            // Given: 콘텐츠와 태그
            val contentId = UUID.randomUUID()
            val tag = Tag(id = 1L, name = "Kotlin", normalizedName = "kotlin", usageCount = 0)

            every { tagRepository.findByNormalizedNames(listOf("kotlin")) } returns Flux.just(tag)
            every { contentTagRepository.findExistingTagIds(contentId, listOf(1L)) } returns Flux.empty()
            every { contentTagRepository.saveAll(any<List<ContentTag>>()) } returns Flux.just(
                ContentTag(id = 1L, contentId = contentId, tagId = 1L)
            )
            every { tagRepository.incrementUsageCountBatch(listOf(1L)) } returns Mono.just(1)

            // When: 태그 연결
            val result = tagService.attachTagsToContent(contentId, listOf("Kotlin"), "test-user")

            // Then: 태그 연결됨, usage_count 증가
            StepVerifier.create(result)
                .assertNext { attachedTag ->
                    assertThat(attachedTag.id).isEqualTo(1L)
                }
                .verifyComplete()

            verify(exactly = 1) { contentTagRepository.saveAll(any<List<ContentTag>>()) }
            verify(exactly = 1) { tagRepository.incrementUsageCountBatch(listOf(1L)) }
        }

        @Test
        @DisplayName("이미 연결된 태그는 중복 연결하지 않는다")
        fun attachTagsToContent_WithExistingRelation_DoesNotDuplicate() {
            // Given: 이미 연결된 태그
            val contentId = UUID.randomUUID()
            val tag = Tag(id = 1L, name = "Java", normalizedName = "java", usageCount = 5)

            every { tagRepository.findByNormalizedNames(listOf("java")) } returns Flux.just(tag)
            every { contentTagRepository.findExistingTagIds(contentId, listOf(1L)) } returns Flux.just(1L)

            // When: 태그 연결 시도
            val result = tagService.attachTagsToContent(contentId, listOf("Java"), "test-user")

            // Then: 저장 안 함, usage_count 증가 안 함
            StepVerifier.create(result)
                .assertNext { attachedTag ->
                    assertThat(attachedTag.id).isEqualTo(1L)
                }
                .verifyComplete()

            verify(exactly = 0) { contentTagRepository.saveAll(any<List<ContentTag>>()) }
            verify(exactly = 0) { tagRepository.incrementUsageCountBatch(any()) }
        }
    }

    @Nested
    @DisplayName("detachTagsFromContent - 콘텐츠에서 태그 제거")
    inner class DetachTagsFromContent {

        @Test
        @DisplayName("콘텐츠의 모든 태그를 제거하면, usage_count가 감소한다")
        fun detachTagsFromContent_RemovesTagsAndDecrementsUsageCount() {
            // Given: 콘텐츠에 연결된 태그
            val contentId = UUID.randomUUID()
            every { contentTagRepository.findTagIdsByContentId(contentId) } returns Flux.just(1L, 2L)
            every { contentTagRepository.deleteByContentId(contentId, "test-user") } returns Mono.just(2)
            every { tagRepository.decrementUsageCountBatch(listOf(1L, 2L)) } returns Mono.just(2)

            // When: 태그 제거
            val result = tagService.detachTagsFromContent(contentId, "test-user")

            // Then: 2개 제거, usage_count 배치 감소
            StepVerifier.create(result)
                .assertNext { count ->
                    assertThat(count).isEqualTo(2)
                }
                .verifyComplete()

            verify(exactly = 1) { contentTagRepository.deleteByContentId(contentId, "test-user") }
            verify(exactly = 1) { tagRepository.decrementUsageCountBatch(listOf(1L, 2L)) }
        }
    }

    @Nested
    @DisplayName("getTagsByContentId - 콘텐츠의 태그 조회")
    inner class GetTagsByContentId {

        @Test
        @DisplayName("콘텐츠에 연결된 태그 목록을 반환한다")
        fun getTagsByContentId_ReturnsTagList() {
            // Given: 콘텐츠에 2개 태그 연결
            val contentId = UUID.randomUUID()
            val tag1 = Tag(id = 1L, name = "Tag1", normalizedName = "tag1", usageCount = 5)
            val tag2 = Tag(id = 2L, name = "Tag2", normalizedName = "tag2", usageCount = 3)

            every { contentTagRepository.findTagIdsByContentId(contentId) } returns Flux.just(1L, 2L)
            every { tagRepository.findByIds(listOf(1L, 2L)) } returns Flux.just(tag1, tag2)

            // When: 태그 조회
            val result = tagService.getTagsByContentId(contentId)

            // Then: 2개 태그 반환
            StepVerifier.create(result)
                .assertNext { tag -> assertThat(tag.id).isEqualTo(1L) }
                .assertNext { tag -> assertThat(tag.id).isEqualTo(2L) }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("getPopularTags - 인기 태그 조회")
    inner class GetPopularTags {

        @Test
        @DisplayName("usage_count 기준 인기 태그를 반환한다")
        fun getPopularTags_ReturnsPopularTags() {
            // Given: 인기 태그 목록
            val tag1 = Tag(id = 1L, name = "Popular1", normalizedName = "popular1", usageCount = 100)
            val tag2 = Tag(id = 2L, name = "Popular2", normalizedName = "popular2", usageCount = 50)

            every { tagRepository.findPopularTags(20) } returns Flux.just(tag1, tag2)

            // When: 인기 태그 조회
            val result = tagService.getPopularTags(20)

            // Then: 인기 태그 반환
            StepVerifier.create(result)
                .assertNext { tag -> assertThat(tag.usageCount).isEqualTo(100) }
                .assertNext { tag -> assertThat(tag.usageCount).isEqualTo(50) }
                .verifyComplete()
        }
    }
}
