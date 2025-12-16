package me.onetwo.upvy.domain.block.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.domain.block.dto.BlockedContentItemResponse
import me.onetwo.upvy.domain.block.exception.BlockException
import me.onetwo.upvy.domain.block.model.ContentBlock
import me.onetwo.upvy.domain.block.repository.ContentBlockRepository
import org.assertj.core.api.Assertions.assertThat
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
 * 콘텐츠 차단 Service 테스트
 *
 * ContentBlockServiceImpl의 비즈니스 로직을 테스트합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("콘텐츠 차단 Service 테스트")
class ContentBlockServiceImplTest : BaseReactiveTest {

    private val contentBlockRepository: ContentBlockRepository = mockk()
    private val contentBlockService: ContentBlockService = ContentBlockServiceImpl(contentBlockRepository)

    @Nested
    @DisplayName("blockContent - 콘텐츠 차단")
    inner class BlockContent {

        @Test
        @DisplayName("유효한 요청으로 콘텐츠를 차단한다")
        fun blockContent_WithValidRequest_BlocksContent() {
            // Given: 차단할 콘텐츠 정보
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val contentBlock = ContentBlock(
                id = 1L,
                userId = userId,
                contentId = contentId,
                createdAt = Instant.now(),
                createdBy = userId.toString(),
                updatedAt = Instant.now(),
                updatedBy = userId.toString(),
                deletedAt = null
            )

            every { contentBlockRepository.save(userId, contentId) } returns Mono.just(contentBlock)

            // When: 콘텐츠 차단
            val result = contentBlockService.blockContent(userId, contentId)

            // Then: 차단 성공
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.id).isEqualTo(1L)
                    assertThat(response.userId).isEqualTo(userId.toString())
                    assertThat(response.contentId).isEqualTo(contentId.toString())
                    assertThat(response.createdAt).isNotNull()
                }
                .verifyComplete()

            verify(exactly = 1) { contentBlockRepository.save(userId, contentId) }
        }
    }

    @Nested
    @DisplayName("unblockContent - 콘텐츠 차단 해제")
    inner class UnblockContent {

        @Test
        @DisplayName("유효한 요청으로 콘텐츠 차단을 해제한다")
        fun unblockContent_WithValidRequest_UnblocksContent() {
            // Given: 차단된 콘텐츠
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { contentBlockRepository.exists(userId, contentId) } returns Mono.just(true)
            every { contentBlockRepository.delete(userId, contentId) } returns Mono.empty()

            // When: 차단 해제
            val result = contentBlockService.unblockContent(userId, contentId)

            // Then: 해제 성공
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) { contentBlockRepository.exists(userId, contentId) }
            verify(exactly = 1) { contentBlockRepository.delete(userId, contentId) }
        }

        @Test
        @DisplayName("차단하지 않은 콘텐츠인 경우 ContentBlockNotFoundException을 발생시킨다")
        fun unblockContent_WhenNotBlocked_ThrowsContentBlockNotFoundException() {
            // Given: 차단하지 않은 콘텐츠
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { contentBlockRepository.exists(userId, contentId) } returns Mono.just(false)

            // When: 차단 해제 시도
            val result = contentBlockService.unblockContent(userId, contentId)

            // Then: ContentBlockNotFoundException 발생
            StepVerifier.create(result)
                .expectError(BlockException.ContentBlockNotFoundException::class.java)
                .verify()

            verify(exactly = 1) { contentBlockRepository.exists(userId, contentId) }
            verify(exactly = 0) { contentBlockRepository.delete(any(), any()) }
        }
    }

    @Nested
    @DisplayName("getBlockedContents - 차단한 콘텐츠 목록 조회")
    inner class GetBlockedContents {

        @Test
        @DisplayName("차단한 콘텐츠 목록을 반환한다")
        fun getBlockedContents_ReturnsBlockedContentsList() {
            // Given: 차단한 콘텐츠 목록
            val userId = UUID.randomUUID()
            val blockedContent1 = BlockedContentItemResponse(
                blockId = 1L,
                contentId = UUID.randomUUID().toString(),
                title = "Blocked Content 1",
                thumbnailUrl = "https://example.com/thumb1.jpg",
                creatorNickname = "Creator 1",
                blockedAt = Instant.now()
            )
            val blockedContent2 = BlockedContentItemResponse(
                blockId = 2L,
                contentId = UUID.randomUUID().toString(),
                title = "Blocked Content 2",
                thumbnailUrl = "https://example.com/thumb2.jpg",
                creatorNickname = "Creator 2",
                blockedAt = Instant.now()
            )

            every { contentBlockRepository.findBlockedContentsByUserId(userId, null, 21) } returns
                Flux.just(blockedContent1, blockedContent2)

            // When: 차단한 콘텐츠 목록 조회
            val result = contentBlockService.getBlockedContents(userId, null, 20)

            // Then: 목록 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(2)
                    assertThat(response.content[0].blockId).isEqualTo(1L)
                    assertThat(response.content[0].title).isEqualTo("Blocked Content 1")
                    assertThat(response.content[1].blockId).isEqualTo(2L)
                    assertThat(response.content[1].title).isEqualTo("Blocked Content 2")
                    assertThat(response.hasNext).isFalse()
                    assertThat(response.nextCursor).isNull()
                    assertThat(response.count).isEqualTo(2)
                }
                .verifyComplete()

            verify(exactly = 1) { contentBlockRepository.findBlockedContentsByUserId(userId, null, 21) }
        }

        @Test
        @DisplayName("hasNext가 true일 때 nextCursor를 반환한다")
        fun getBlockedContents_WhenHasNext_ReturnsNextCursor() {
            // Given: limit + 1개의 결과
            val userId = UUID.randomUUID()
            val items = (1..21).map { i ->
                BlockedContentItemResponse(
                    blockId = i.toLong(),
                    contentId = UUID.randomUUID().toString(),
                    title = "Blocked Content $i",
                    thumbnailUrl = "https://example.com/thumb$i.jpg",
                    creatorNickname = "Creator $i",
                    blockedAt = Instant.now()
                )
            }

            every { contentBlockRepository.findBlockedContentsByUserId(userId, null, 21) } returns
                Flux.fromIterable(items)

            // When: 차단한 콘텐츠 목록 조회 (limit=20)
            val result = contentBlockService.getBlockedContents(userId, null, 20)

            // Then: hasNext=true, nextCursor 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(20)
                    assertThat(response.hasNext).isTrue()
                    assertThat(response.nextCursor).isEqualTo("20")
                    assertThat(response.count).isEqualTo(20)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("커서를 사용하여 페이지네이션한다")
        fun getBlockedContents_WithCursor_ReturnsPaginatedResults() {
            // Given: 커서 이후의 결과
            val userId = UUID.randomUUID()
            val cursor = "10"
            val blockedContent = BlockedContentItemResponse(
                blockId = 9L,
                contentId = UUID.randomUUID().toString(),
                title = "Blocked Content 9",
                thumbnailUrl = "https://example.com/thumb9.jpg",
                creatorNickname = "Creator 9",
                blockedAt = Instant.now()
            )

            every { contentBlockRepository.findBlockedContentsByUserId(userId, 10L, 21) } returns
                Flux.just(blockedContent)

            // When: 커서로 조회
            val result = contentBlockService.getBlockedContents(userId, cursor, 20)

            // Then: 커서 이후의 결과 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(1)
                    assertThat(response.content[0].blockId).isEqualTo(9L)
                    assertThat(response.hasNext).isFalse()
                }
                .verifyComplete()

            verify(exactly = 1) { contentBlockRepository.findBlockedContentsByUserId(userId, 10L, 21) }
        }

        @Test
        @DisplayName("차단한 콘텐츠가 없으면 빈 목록을 반환한다")
        fun getBlockedContents_WhenNoBlocks_ReturnsEmptyList() {
            // Given: 차단이 없는 상태
            val userId = UUID.randomUUID()

            every { contentBlockRepository.findBlockedContentsByUserId(userId, null, 21) } returns Flux.empty()

            // When: 차단한 콘텐츠 목록 조회
            val result = contentBlockService.getBlockedContents(userId, null, 20)

            // Then: 빈 목록 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).isEmpty()
                    assertThat(response.hasNext).isFalse()
                    assertThat(response.nextCursor).isNull()
                    assertThat(response.count).isEqualTo(0)
                }
                .verifyComplete()
        }
    }
}
