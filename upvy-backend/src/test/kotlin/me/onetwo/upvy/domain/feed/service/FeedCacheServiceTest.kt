package me.onetwo.upvy.domain.feed.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.ReactiveListOperations
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.script.RedisScript
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest

/**
 * 피드 캐시 서비스 테스트
 *
 * Redis를 사용한 피드 캐싱 기능을 검증합니다.
 * MockK를 사용한 단위 테스트입니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("피드 캐시 Service 테스트")
class FeedCacheServiceTest : BaseReactiveTest {

    private lateinit var reactiveRedisTemplate: ReactiveRedisTemplate<String, String>
    private lateinit var listOps: ReactiveListOperations<String, String>
    private lateinit var feedCacheService: FeedCacheService

    private val userId = UUID.randomUUID()
    private val batchNumber = 0
    private val language = "ko"

    @BeforeEach
    fun setUp() {
        reactiveRedisTemplate = mockk(relaxed = true)
        listOps = mockk(relaxed = true)

        every { reactiveRedisTemplate.opsForList() } returns listOps

        feedCacheService = FeedCacheService(reactiveRedisTemplate)
    }

    @Nested
    @DisplayName("getMainFeedBatch - 언어별 메인 피드 배치 조회")
    inner class GetMainFeedBatch {

        @Test
        @DisplayName("캐시에 데이터가 있는 경우, 저장된 콘텐츠 ID 목록을 반환한다")
        fun getMainFeedBatch_WithCachedData_ReturnsContentIds() {
            // Given: Redis에 콘텐츠 ID 배치 저장
            val contentIds = List(250) { UUID.randomUUID() }
            val contentIdStrings = contentIds.map { it.toString() }
            val key = "feed:main:$userId:lang:$language:batch:$batchNumber"

            every { listOps.range(key, 0, -1) } returns Flux.fromIterable(contentIdStrings)

            // When: 캐시에서 배치 조회
            val result = feedCacheService.getMainFeedBatch(userId, language, batchNumber)

            // Then: 저장된 ID 목록이 반환됨
            StepVerifier.create(result)
                .assertNext { cachedIds ->
                    assertEquals(250, cachedIds.size)
                    assertEquals(contentIds, cachedIds)
                }
                .verifyComplete()

            verify(exactly = 1) { listOps.range(key, 0, -1) }
        }

        @Test
        @DisplayName("캐시에 데이터가 없는 경우, empty Mono를 반환한다")
        fun getMainFeedBatch_WithoutCachedData_ReturnsEmpty() {
            // Given: 캐시에 데이터 없음
            val key = "feed:main:$userId:lang:$language:batch:$batchNumber"
            every { listOps.range(key, 0, -1) } returns Flux.empty()

            // When: 존재하지 않는 배치 조회
            val result = feedCacheService.getMainFeedBatch(userId, language, batchNumber)

            // Then: empty Mono 반환
            StepVerifier.create(result)
                .verifyComplete()
        }

        @Test
        @DisplayName("다른 사용자의 배치는 조회되지 않는다")
        fun getMainFeedBatch_WithDifferentUser_ReturnsEmpty() {
            // Given: user1의 배치는 있지만 user2의 배치는 없음
            val user1 = UUID.randomUUID()
            val user2 = UUID.randomUUID()
            val key2 = "feed:main:$user2:lang:$language:batch:$batchNumber"

            every { listOps.range(key2, 0, -1) } returns Flux.empty()

            // When: user2로 조회
            val result = feedCacheService.getMainFeedBatch(user2, language, batchNumber)

            // Then: empty Mono 반환
            StepVerifier.create(result)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("saveMainFeedBatch - 언어별 메인 피드 배치 저장")
    inner class SaveMainFeedBatch {

        @Test
        @DisplayName("콘텐츠 ID 목록을 Redis에 저장하고 true를 반환한다")
        fun saveMainFeedBatch_WithValidData_SavesAndReturnsTrue() {
            // Given: 저장할 콘텐츠 ID 목록
            val contentIds = List(250) { UUID.randomUUID() }

            // Lua 스크립트 실행 결과 mock
            every {
                reactiveRedisTemplate.execute(
                    any<RedisScript<Long>>(),
                    any<List<String>>(),
                    any<List<String>>()
                )
            } returns Flux.just(1L)

            // When: Redis에 저장
            val result = feedCacheService.saveMainFeedBatch(userId, language, batchNumber, contentIds)

            // Then: true 반환
            StepVerifier.create(result)
                .assertNext { success ->
                    assertTrue(success)
                }
                .verifyComplete()

            verify(exactly = 1) {
                reactiveRedisTemplate.execute(
                    any<RedisScript<Long>>(),
                    any<List<String>>(),
                    any<List<String>>()
                )
            }
        }

        @Test
        @DisplayName("빈 리스트를 저장하려는 경우, false를 반환한다")
        fun saveMainFeedBatch_WithEmptyList_ReturnsFalse() {
            // Given: 빈 리스트
            val emptyList = emptyList<UUID>()

            // When: 빈 리스트 저장 시도
            val result = feedCacheService.saveMainFeedBatch(userId, language, batchNumber, emptyList)

            // Then: false 반환
            StepVerifier.create(result)
                .assertNext { success ->
                    assertFalse(success)
                }
                .verifyComplete()

            // Then: Redis 호출 없음 (execute가 호출되지 않아야 함)
            verify(exactly = 0) {
                reactiveRedisTemplate.execute(
                    any<RedisScript<Long>>(),
                    any<List<String>>(),
                    any<List<String>>()
                )
            }
        }

        @Test
        @DisplayName("TTL이 30분으로 설정된다")
        fun saveMainFeedBatch_SetsTTL() {
            // Given: 저장할 콘텐츠 ID 목록
            val contentIds = List(10) { UUID.randomUUID() }

            // Lua 스크립트 실행 결과 mock
            every {
                reactiveRedisTemplate.execute(
                    any<RedisScript<Long>>(),
                    any<List<String>>(),
                    any<List<String>>()
                )
            } returns Flux.just(1L)

            // When: Redis에 저장
            feedCacheService.saveMainFeedBatch(userId, language, batchNumber, contentIds).block()

            // Then: Lua 스크립트가 TTL과 함께 실행됨 (스크립트 내부에서 EXPIRE 호출)
            verify(exactly = 1) {
                reactiveRedisTemplate.execute(
                    any<RedisScript<Long>>(),
                    any<List<String>>(),
                    any<List<String>>()
                )
            }
        }
    }

    @Nested
    @DisplayName("getMainFeedBatchSize - 언어별 메인 피드 배치 크기 조회")
    inner class GetMainFeedBatchSize {

        @Test
        @DisplayName("저장된 배치의 크기를 반환한다")
        fun getMainFeedBatchSize_WithCachedData_ReturnsSize() {
            // Given: 250개의 콘텐츠 ID 저장됨
            val key = "feed:main:$userId:lang:$language:batch:$batchNumber"
            every { listOps.size(key) } returns Mono.just(250L)

            // When: 배치 크기 조회
            val result = feedCacheService.getMainFeedBatchSize(userId, language, batchNumber)

            // Then: 250 반환
            StepVerifier.create(result)
                .assertNext { size ->
                    assertEquals(250L, size)
                }
                .verifyComplete()

            verify(exactly = 1) { listOps.size(key) }
        }

        @Test
        @DisplayName("캐시에 데이터가 없는 경우, 0을 반환한다")
        fun getMainFeedBatchSize_WithoutCachedData_ReturnsZero() {
            // Given: 캐시에 데이터 없음
            val key = "feed:main:$userId:lang:$language:batch:$batchNumber"
            every { listOps.size(key) } returns Mono.just(0L)

            // When: 존재하지 않는 배치 크기 조회
            val result = feedCacheService.getMainFeedBatchSize(userId, language, batchNumber)

            // Then: 0 반환
            StepVerifier.create(result)
                .assertNext { size ->
                    assertEquals(0L, size)
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("clearAllMainFeedCache - 사용자의 모든 언어별 메인 피드 캐시 삭제")
    inner class ClearAllMainFeedCache {

        @Test
        @DisplayName("사용자의 모든 언어별 배치를 삭제하고 true를 반환한다")
        fun clearAllMainFeedCache_DeletesAllBatchesAndReturnsTrue() {
            // Given: 사용자의 여러 언어별 배치가 있음
            val keys = listOf(
                "feed:main:$userId:lang:ko:batch:0",
                "feed:main:$userId:lang:ko:batch:1",
                "feed:main:$userId:lang:en:batch:0"
            )

            every { reactiveRedisTemplate.scan(any<ScanOptions>()) } returns Flux.fromIterable(keys)
            every { reactiveRedisTemplate.delete(any<Flux<String>>()) } returns Mono.just(3L)

            // When: 사용자의 모든 메인 피드 캐시 삭제
            val result = feedCacheService.clearAllMainFeedCache(userId)

            // Then: true 반환
            StepVerifier.create(result)
                .assertNext { success ->
                    assertTrue(success)
                }
                .verifyComplete()

            verify(exactly = 1) { reactiveRedisTemplate.scan(any<ScanOptions>()) }
            verify(exactly = 1) { reactiveRedisTemplate.delete(any<Flux<String>>()) }
        }

        @Test
        @DisplayName("삭제할 캐시가 없는 경우에도 true를 반환한다")
        fun clearAllMainFeedCache_WithoutCachedData_ReturnsTrue() {
            // Given: 캐시에 데이터 없음
            every { reactiveRedisTemplate.scan(any<ScanOptions>()) } returns Flux.empty()

            // When: 캐시 삭제 시도
            val result = feedCacheService.clearAllMainFeedCache(userId)

            // Then: true 반환
            StepVerifier.create(result)
                .assertNext { success ->
                    assertTrue(success)
                }
                .verifyComplete()

            verify(exactly = 1) { reactiveRedisTemplate.scan(any<ScanOptions>()) }
            verify(exactly = 0) { reactiveRedisTemplate.delete(any<Flux<String>>()) }
        }

        @Test
        @DisplayName("다른 사용자의 캐시는 삭제되지 않는다")
        fun clearAllMainFeedCache_DoesNotDeleteOtherUsersCache() {
            // Given: user1의 캐시만 있음
            val user1 = UUID.randomUUID()
            val keys = listOf("feed:main:$user1:lang:ko:batch:0")

            every { reactiveRedisTemplate.scan(any<ScanOptions>()) } returns Flux.fromIterable(keys)
            every { reactiveRedisTemplate.delete(any<Flux<String>>()) } returns Mono.just(1L)

            // When: user1의 캐시만 삭제
            feedCacheService.clearAllMainFeedCache(user1).block()

            // Then: scan과 delete 호출 확인
            verify(exactly = 1) { reactiveRedisTemplate.scan(any<ScanOptions>()) }
            verify(exactly = 1) { reactiveRedisTemplate.delete(any<Flux<String>>()) }
        }
    }
}
