package me.onetwo.growsnap.domain.feed.service

import me.onetwo.growsnap.domain.content.model.Category
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID

/**
 * 피드 캐시 서비스
 *
 * Redis를 사용하여 추천 피드 결과를 캐싱합니다.
 * TikTok/Instagram Reels와 같은 숏폼 피드의 성능 최적화를 위해
 * 세션 기반으로 추천 결과를 미리 계산하여 저장합니다.
 *
 * ### 캐싱 전략
 * - 250개의 콘텐츠 ID를 미리 생성하여 캐싱
 * - TTL: 30분 (사용자 세션 유지 시간)
 * - 메인 피드: `feed:main:{userId}:lang:{language}:batch:{batchNumber}` (언어별 캐싱)
 * - 카테고리 피드: `feed:category:{userId}:{category}:batch:{batchNumber}`
 *
 * @property redisTemplate Reactive Redis Template
 */
@Service
class FeedCacheService(
    private val redisTemplate: ReactiveRedisTemplate<String, String>
) {

    /**
     * 언어별 카테고리 추천 피드 배치 조회
     *
     * Redis에서 캐싱된 언어별 카테고리 추천 콘텐츠 ID 배치를 조회합니다.
     * 캐시가 없으면 empty Mono를 반환합니다.
     *
     * Issue #107: 카테고리 피드에도 언어 가중치 적용을 위해 언어별 캐싱 지원 추가
     *
     * @param userId 사용자 ID
     * @param category 카테고리
     * @param language 언어 (ISO 639-1, 예: ko, en)
     * @param batchNumber 배치 번호 (0부터 시작)
     * @return 추천 콘텐츠 ID 목록 (최대 250개)
     */
    fun getCategoryRecommendationBatch(userId: UUID, category: Category, language: String, batchNumber: Int): Mono<List<UUID>> {
        val key = buildCategoryBatchKey(userId, category, language, batchNumber)

        return redisTemplate.opsForList()
            .range(key, 0, -1)  // 전체 조회
            .map { UUID.fromString(it) }
            .collectList()
            .filter { it.isNotEmpty() }  // 빈 리스트는 캐시 miss로 처리
    }

    /**
     * 언어별 카테고리 추천 피드 배치 저장
     *
     * 언어별 카테고리 추천 알고리즘 결과를 Redis에 캐싱합니다.
     * TTL은 30분으로 설정됩니다.
     *
     * Lua 스크립트를 사용하여 RPUSH와 EXPIRE를 원자적으로 실행합니다.
     *
     * Issue #107: 카테고리 피드에도 언어 가중치 적용을 위해 언어별 캐싱 지원 추가
     *
     * @param userId 사용자 ID
     * @param category 카테고리
     * @param language 언어 (ISO 639-1, 예: ko, en)
     * @param batchNumber 배치 번호
     * @param contentIds 추천 콘텐츠 ID 목록
     * @return 저장 완료 신호
     */
    fun saveCategoryRecommendationBatch(
        userId: UUID,
        category: Category,
        language: String,
        batchNumber: Int,
        contentIds: List<UUID>
    ): Mono<Boolean> {
        if (contentIds.isEmpty()) {
            return Mono.just(false)
        }

        val key = buildCategoryBatchKey(userId, category, language, batchNumber)
        val values = contentIds.map { it.toString() }
        val ttlSeconds = batchTtl.seconds

        // Lua 스크립트: RPUSH와 EXPIRE를 원자적으로 실행
        val scriptText = """
            redis.call('DEL', KEYS[1])
            for i = 1, #ARGV - 1 do
                redis.call('RPUSH', KEYS[1], ARGV[i])
            end
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[#ARGV]))
            return 1
        """.trimIndent()

        val script = DefaultRedisScript<Long>()
        script.setScriptText(scriptText)
        script.setResultType(Long::class.java)

        val scriptArgs = values + ttlSeconds.toString()

        return redisTemplate.execute(script, listOf(key), scriptArgs)
            .next()
            .map { it > 0 }
    }

    /**
     * 언어별 카테고리 배치 크기 조회
     *
     * Issue #107: 카테고리 피드에도 언어 가중치 적용을 위해 언어별 캐싱 지원 추가
     *
     * @param userId 사용자 ID
     * @param category 카테고리
     * @param language 언어 (ISO 639-1, 예: ko, en)
     * @param batchNumber 배치 번호
     * @return 배치에 저장된 ID 개수
     */
    fun getCategoryBatchSize(userId: UUID, category: Category, language: String, batchNumber: Int): Mono<Long> {
        val key = buildCategoryBatchKey(userId, category, language, batchNumber)
        return redisTemplate.opsForList().size(key)
    }

    /**
     * 특정 카테고리의 모든 배치 삭제 (모든 언어)
     *
     * 사용자의 특정 카테고리 추천 캐시를 모든 언어에 대해 삭제합니다.
     * 재추천이 필요할 때 사용합니다.
     *
     * Issue #107: 언어별 캐싱을 지원하므로 모든 언어 캐시 삭제
     *
     * @param userId 사용자 ID
     * @param category 카테고리
     * @return 삭제 완료 신호
     */
    fun clearCategoryCache(userId: UUID, category: Category): Mono<Boolean> {
        val pattern = "feed:category:$userId:${category.name}:lang:*:batch:*"
        val scanOptions = ScanOptions.scanOptions()
            .match(pattern)
            .build()

        return redisTemplate.scan(scanOptions)
            .collectList()
            .flatMap { keys ->
                if (keys.isEmpty()) {
                    Mono.just(true)
                } else {
                    redisTemplate.delete(Flux.fromIterable(keys))
                        .map { it > 0 }
                }
            }
    }

    /**
     * 언어별 카테고리 Redis 키 생성
     *
     * Issue #107: 카테고리 피드에도 언어 가중치 적용을 위해 언어별 캐싱 지원 추가
     *
     * @param userId 사용자 ID
     * @param category 카테고리
     * @param language 언어 (ISO 639-1, 예: ko, en)
     * @param batchNumber 배치 번호
     * @return Redis key (예: feed:category:{userId}:{category}:lang:{language}:batch:{batchNumber})
     */
    private fun buildCategoryBatchKey(userId: UUID, category: Category, language: String, batchNumber: Int): String {
        return "feed:category:$userId:${category.name}:lang:$language:batch:$batchNumber"
    }

    /**
     * 언어별 메인 피드 배치 조회
     *
     * Redis에서 캐싱된 언어별 메인 피드 배치를 조회합니다.
     * 캐시가 없으면 empty Mono를 반환합니다.
     *
     * @param userId 사용자 ID
     * @param language 언어 (ISO 639-1, 예: ko, en)
     * @param batchNumber 배치 번호 (0부터 시작)
     * @return 추천 콘텐츠 ID 목록 (최대 250개)
     */
    fun getMainFeedBatch(userId: UUID, language: String, batchNumber: Int): Mono<List<UUID>> {
        val key = buildMainFeedBatchKey(userId, language, batchNumber)

        return redisTemplate.opsForList()
            .range(key, 0, -1)  // 전체 조회
            .map { UUID.fromString(it) }
            .collectList()
            .filter { it.isNotEmpty() }  // 빈 리스트는 캐시 miss로 처리
    }

    /**
     * 언어별 메인 피드 배치 저장
     *
     * 언어별 메인 피드 추천 결과를 Redis에 캐싱합니다.
     * TTL은 30분으로 설정됩니다.
     *
     * Lua 스크립트를 사용하여 RPUSH와 EXPIRE를 원자적으로 실행합니다.
     *
     * @param userId 사용자 ID
     * @param language 언어 (ISO 639-1)
     * @param batchNumber 배치 번호
     * @param contentIds 추천 콘텐츠 ID 목록
     * @return 저장 완료 신호
     */
    fun saveMainFeedBatch(
        userId: UUID,
        language: String,
        batchNumber: Int,
        contentIds: List<UUID>
    ): Mono<Boolean> {
        if (contentIds.isEmpty()) {
            return Mono.just(false)
        }

        val key = buildMainFeedBatchKey(userId, language, batchNumber)
        val values = contentIds.map { it.toString() }
        val ttlSeconds = batchTtl.seconds

        // Lua 스크립트: RPUSH와 EXPIRE를 원자적으로 실행
        val scriptText = """
            redis.call('DEL', KEYS[1])
            for i = 1, #ARGV - 1 do
                redis.call('RPUSH', KEYS[1], ARGV[i])
            end
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[#ARGV]))
            return 1
        """.trimIndent()

        val script = DefaultRedisScript<Long>()
        script.setScriptText(scriptText)
        script.setResultType(Long::class.java)

        val scriptArgs = values + ttlSeconds.toString()

        return redisTemplate.execute(script, listOf(key), scriptArgs)
            .next()
            .map { it > 0 }
    }

    /**
     * 언어별 메인 피드 배치 크기 조회
     *
     * @param userId 사용자 ID
     * @param language 언어 (ISO 639-1)
     * @param batchNumber 배치 번호
     * @return 배치에 저장된 ID 개수
     */
    fun getMainFeedBatchSize(userId: UUID, language: String, batchNumber: Int): Mono<Long> {
        val key = buildMainFeedBatchKey(userId, language, batchNumber)
        return redisTemplate.opsForList().size(key)
    }

    /**
     * 사용자의 모든 메인 피드 캐시 삭제 (모든 언어)
     *
     * 사용자의 모든 언어별 메인 피드 캐시를 삭제합니다.
     * 피드 새로고침 시 사용합니다.
     *
     * @param userId 사용자 ID
     * @return 삭제 완료 신호
     */
    fun clearAllMainFeedCache(userId: UUID): Mono<Boolean> {
        val pattern = "feed:main:$userId:lang:*:batch:*"
        val scanOptions = ScanOptions.scanOptions()
            .match(pattern)
            .build()

        return redisTemplate.scan(scanOptions)
            .collectList()
            .flatMap { keys ->
                if (keys.isEmpty()) {
                    Mono.just(true)
                } else {
                    redisTemplate.delete(Flux.fromIterable(keys))
                        .map { it > 0 }
                }
            }
    }

    /**
     * 언어별 메인 피드 Redis 키 생성
     *
     * @param userId 사용자 ID
     * @param language 언어 (ISO 639-1)
     * @param batchNumber 배치 번호
     * @return Redis key
     */
    private fun buildMainFeedBatchKey(userId: UUID, language: String, batchNumber: Int): String {
        return "feed:main:$userId:lang:$language:batch:$batchNumber"
    }

    companion object {
        /**
         * 배치당 콘텐츠 개수
         *
         * TikTok/Instagram Reels 방식: 250개를 미리 계산하여 캐싱
         */
        const val BATCH_SIZE = 250

        /**
         * 배치 TTL (30분)
         *
         * 사용자 세션 유지 시간
         */
        val batchTtl: Duration = Duration.ofMinutes(30)

        /**
         * Prefetch 임계값 (50%)
         *
         * 사용자가 배치의 50%를 소진하면 다음 배치를 미리 생성
         */
        const val PREFETCH_THRESHOLD = 0.5
    }
}
