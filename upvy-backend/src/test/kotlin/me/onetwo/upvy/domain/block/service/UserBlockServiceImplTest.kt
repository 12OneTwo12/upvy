package me.onetwo.upvy.domain.block.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.domain.block.dto.BlockedUserItemResponse
import me.onetwo.upvy.domain.block.exception.BlockException
import me.onetwo.upvy.domain.block.model.UserBlock
import me.onetwo.upvy.domain.block.repository.UserBlockRepository
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
 * 사용자 차단 Service 테스트
 *
 * UserBlockServiceImpl의 비즈니스 로직을 테스트합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("사용자 차단 Service 테스트")
class UserBlockServiceImplTest : BaseReactiveTest {

    private val userBlockRepository: UserBlockRepository = mockk()
    private val userBlockService: UserBlockService = UserBlockServiceImpl(userBlockRepository)

    @Nested
    @DisplayName("blockUser - 사용자 차단")
    inner class BlockUser {

        @Test
        @DisplayName("유효한 요청으로 사용자를 차단한다")
        fun blockUser_WithValidRequest_BlocksUser() {
            // Given: 차단할 사용자 정보
            val blockerId = UUID.randomUUID()
            val blockedId = UUID.randomUUID()
            val userBlock = UserBlock(
                id = 1L,
                blockerId = blockerId,
                blockedId = blockedId,
                createdAt = Instant.now(),
                createdBy = blockerId.toString(),
                updatedAt = Instant.now(),
                updatedBy = blockerId.toString(),
                deletedAt = null
            )

            every { userBlockRepository.save(blockerId, blockedId) } returns Mono.just(userBlock)

            // When: 사용자 차단
            val result = userBlockService.blockUser(blockerId, blockedId)

            // Then: 차단 성공
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.id).isEqualTo(1L)
                    assertThat(response.blockerId).isEqualTo(blockerId.toString())
                    assertThat(response.blockedId).isEqualTo(blockedId.toString())
                    assertThat(response.createdAt).isNotNull()
                }
                .verifyComplete()

            verify(exactly = 1) { userBlockRepository.save(blockerId, blockedId) }
        }

        @Test
        @DisplayName("자기 자신을 차단하려는 경우 SelfBlockException을 발생시킨다")
        fun blockUser_WhenBlockingSelf_ThrowsSelfBlockException() {
            // Given: 동일한 사용자 ID
            val userId = UUID.randomUUID()

            // When: 자기 자신 차단 시도
            val result = userBlockService.blockUser(userId, userId)

            // Then: SelfBlockException 발생
            StepVerifier.create(result)
                .expectError(BlockException.SelfBlockException::class.java)
                .verify()

            verify(exactly = 0) { userBlockRepository.save(any(), any()) }
        }
    }

    @Nested
    @DisplayName("unblockUser - 사용자 차단 해제")
    inner class UnblockUser {

        @Test
        @DisplayName("유효한 요청으로 사용자 차단을 해제한다")
        fun unblockUser_WithValidRequest_UnblocksUser() {
            // Given: 차단된 사용자
            val blockerId = UUID.randomUUID()
            val blockedId = UUID.randomUUID()

            every { userBlockRepository.exists(blockerId, blockedId) } returns Mono.just(true)
            every { userBlockRepository.delete(blockerId, blockedId) } returns Mono.empty()

            // When: 차단 해제
            val result = userBlockService.unblockUser(blockerId, blockedId)

            // Then: 해제 성공
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) { userBlockRepository.exists(blockerId, blockedId) }
            verify(exactly = 1) { userBlockRepository.delete(blockerId, blockedId) }
        }

        @Test
        @DisplayName("차단하지 않은 사용자인 경우 UserBlockNotFoundException을 발생시킨다")
        fun unblockUser_WhenNotBlocked_ThrowsUserBlockNotFoundException() {
            // Given: 차단하지 않은 사용자
            val blockerId = UUID.randomUUID()
            val blockedId = UUID.randomUUID()

            every { userBlockRepository.exists(blockerId, blockedId) } returns Mono.just(false)

            // When: 차단 해제 시도
            val result = userBlockService.unblockUser(blockerId, blockedId)

            // Then: UserBlockNotFoundException 발생
            StepVerifier.create(result)
                .expectError(BlockException.UserBlockNotFoundException::class.java)
                .verify()

            verify(exactly = 1) { userBlockRepository.exists(blockerId, blockedId) }
            verify(exactly = 0) { userBlockRepository.delete(any(), any()) }
        }
    }

    @Nested
    @DisplayName("getBlockedUsers - 차단한 사용자 목록 조회")
    inner class GetBlockedUsers {

        @Test
        @DisplayName("차단한 사용자 목록을 반환한다")
        fun getBlockedUsers_ReturnsBlockedUsersList() {
            // Given: 차단한 사용자 목록
            val blockerId = UUID.randomUUID()
            val blockedUser1 = BlockedUserItemResponse(
                blockId = 1L,
                userId = UUID.randomUUID().toString(),
                nickname = "Blocked User 1",
                profileImageUrl = null,
                blockedAt = Instant.now()
            )
            val blockedUser2 = BlockedUserItemResponse(
                blockId = 2L,
                userId = UUID.randomUUID().toString(),
                nickname = "Blocked User 2",
                profileImageUrl = null,
                blockedAt = Instant.now()
            )

            every { userBlockRepository.findBlockedUsersByBlockerId(blockerId, null, 21) } returns
                Flux.just(blockedUser1, blockedUser2)

            // When: 차단한 사용자 목록 조회
            val result = userBlockService.getBlockedUsers(blockerId, null, 20)

            // Then: 목록 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(2)
                    assertThat(response.content[0].blockId).isEqualTo(1L)
                    assertThat(response.content[0].nickname).isEqualTo("Blocked User 1")
                    assertThat(response.content[1].blockId).isEqualTo(2L)
                    assertThat(response.content[1].nickname).isEqualTo("Blocked User 2")
                    assertThat(response.hasNext).isFalse()
                    assertThat(response.nextCursor).isNull()
                    assertThat(response.count).isEqualTo(2)
                }
                .verifyComplete()

            verify(exactly = 1) { userBlockRepository.findBlockedUsersByBlockerId(blockerId, null, 21) }
        }

        @Test
        @DisplayName("hasNext가 true일 때 nextCursor를 반환한다")
        fun getBlockedUsers_WhenHasNext_ReturnsNextCursor() {
            // Given: limit + 1개의 결과
            val blockerId = UUID.randomUUID()
            val items = (1..21).map { i ->
                BlockedUserItemResponse(
                    blockId = i.toLong(),
                    userId = UUID.randomUUID().toString(),
                    nickname = "Blocked User $i",
                    profileImageUrl = null,
                    blockedAt = Instant.now()
                )
            }

            every { userBlockRepository.findBlockedUsersByBlockerId(blockerId, null, 21) } returns
                Flux.fromIterable(items)

            // When: 차단한 사용자 목록 조회 (limit=20)
            val result = userBlockService.getBlockedUsers(blockerId, null, 20)

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
        fun getBlockedUsers_WithCursor_ReturnsPaginatedResults() {
            // Given: 커서 이후의 결과
            val blockerId = UUID.randomUUID()
            val cursor = "10"
            val blockedUser = BlockedUserItemResponse(
                blockId = 9L,
                userId = UUID.randomUUID().toString(),
                nickname = "Blocked User 9",
                profileImageUrl = null,
                blockedAt = Instant.now()
            )

            every { userBlockRepository.findBlockedUsersByBlockerId(blockerId, 10L, 21) } returns
                Flux.just(blockedUser)

            // When: 커서로 조회
            val result = userBlockService.getBlockedUsers(blockerId, cursor, 20)

            // Then: 커서 이후의 결과 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(1)
                    assertThat(response.content[0].blockId).isEqualTo(9L)
                    assertThat(response.hasNext).isFalse()
                }
                .verifyComplete()

            verify(exactly = 1) { userBlockRepository.findBlockedUsersByBlockerId(blockerId, 10L, 21) }
        }

        @Test
        @DisplayName("차단한 사용자가 없으면 빈 목록을 반환한다")
        fun getBlockedUsers_WhenNoBlocks_ReturnsEmptyList() {
            // Given: 차단이 없는 상태
            val blockerId = UUID.randomUUID()

            every { userBlockRepository.findBlockedUsersByBlockerId(blockerId, null, 21) } returns Flux.empty()

            // When: 차단한 사용자 목록 조회
            val result = userBlockService.getBlockedUsers(blockerId, null, 20)

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
