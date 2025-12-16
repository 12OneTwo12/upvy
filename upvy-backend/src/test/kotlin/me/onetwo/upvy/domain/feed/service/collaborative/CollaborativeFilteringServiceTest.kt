package me.onetwo.upvy.domain.feed.service.collaborative

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.domain.analytics.dto.InteractionType
import me.onetwo.upvy.domain.analytics.dto.UserInteraction
import me.onetwo.upvy.domain.analytics.repository.UserContentInteractionRepository
import me.onetwo.upvy.domain.content.dto.ContentWithMetadata
import me.onetwo.upvy.domain.content.model.Category
import me.onetwo.upvy.domain.content.model.Content
import me.onetwo.upvy.domain.content.model.ContentMetadata
import me.onetwo.upvy.domain.content.model.ContentStatus
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.domain.content.repository.ContentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 협업 필터링 서비스 테스트
 *
 * Item-based Collaborative Filtering 알고리즘을 검증합니다.
 *
 * ## 테스트 시나리오
 *
 * ### 정상 케이스
 * 1. 나와 비슷한 사용자가 좋아한 콘텐츠를 추천
 * 2. 인터랙션 타입별 가중치 적용 (LIKE=1.0, SAVE=1.5, SHARE=2.0)
 * 3. 여러 사용자가 공통으로 좋아한 콘텐츠는 높은 점수
 *
 * ### 엣지 케이스
 * 1. 신규 사용자 (인터랙션 없음): 빈 결과 반환
 * 2. 유사 사용자 없음: 빈 결과 반환
 * 3. 모든 추천 콘텐츠를 이미 봄: 빈 결과 반환
 */
@ExtendWith(MockKExtension::class)
@DisplayName("Collaborative Filtering Service 테스트")
class CollaborativeFilteringServiceTest : BaseReactiveTest {

    private lateinit var userContentInteractionRepository: UserContentInteractionRepository
    private lateinit var contentRepository: ContentRepository
    private lateinit var collaborativeFilteringService: CollaborativeFilteringService

    private val userId = UUID.randomUUID()
    private val contentId1 = UUID.randomUUID()
    private val contentId2 = UUID.randomUUID()
    private val contentId3 = UUID.randomUUID()
    private val contentId4 = UUID.randomUUID()
    private val contentId5 = UUID.randomUUID()

    private val similarUser1 = UUID.randomUUID()
    private val similarUser2 = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        userContentInteractionRepository = mockk()
        contentRepository = mockk()
        collaborativeFilteringService = CollaborativeFilteringServiceImpl(
            userContentInteractionRepository,
            contentRepository
        )
    }

    /**
     * ContentWithMetadata 생성 헬퍼 함수
     *
     * @param contentId 콘텐츠 ID
     * @param language 언어 (기본값: "en")
     * @param category 카테고리 (기본값: PROGRAMMING)
     * @param createdAt 콘텐츠 생성 시각 (기본값: 현재)
     * @return ContentWithMetadata 객체
     */
    private fun createContentWithMetadata(
        contentId: UUID,
        language: String = "en",
        category: Category = Category.PROGRAMMING,
        createdAt: Instant = Instant.now()
    ): ContentWithMetadata {
        val content = Content(
            id = contentId,
            creatorId = UUID.randomUUID(),
            contentType = ContentType.VIDEO,
            url = "https://example.com/video.mp4",
            thumbnailUrl = "https://example.com/thumbnail.jpg",
            width = 1920,
            height = 1080,
            status = ContentStatus.PUBLISHED,
            createdAt = createdAt
        )
        val metadata = ContentMetadata(
            contentId = contentId,
            title = "Test Content",
            category = category,
            language = language,
            createdAt = createdAt
        )
        return ContentWithMetadata(content, metadata)
    }

    @Nested
    @DisplayName("getRecommendedContents - Item-based CF 추천")
    inner class GetRecommendedContents {

        @Test
        @DisplayName("정상 케이스: 나와 비슷한 사용자가 좋아한 콘텐츠를 추천한다")
        fun getRecommendedContents_WithSimilarUsers_ReturnsRecommendedContents() {
            // Given: 나는 Content1을 좋아요, Content2를 저장
            val myInteractions = listOf(
                UserInteraction(contentId1, InteractionType.LIKE),
                UserInteraction(contentId2, InteractionType.SAVE)
            )

            every {
                userContentInteractionRepository.findAllInteractionsByUser(userId, any())
            } returns Flux.fromIterable(myInteractions)

            // Content1을 좋아한 유사 사용자들
            every {
                userContentInteractionRepository.findUsersByContent(contentId1, null, any())
            } returns Flux.just(similarUser1, similarUser2)

            // Content2를 저장한 유사 사용자들
            every {
                userContentInteractionRepository.findUsersByContent(contentId2, null, any())
            } returns Flux.just(similarUser1)

            // similarUser1이 좋아한 콘텐츠: Content3(좋아요), Content4(공유)
            every {
                userContentInteractionRepository.findAllInteractionsByUser(similarUser1, any())
            } returns Flux.just(
                UserInteraction(contentId3, InteractionType.LIKE),    // 1.0
                UserInteraction(contentId4, InteractionType.SHARE)    // 2.0
            )

            // similarUser2가 좋아한 콘텐츠: Content3(저장), Content5(좋아요)
            every {
                userContentInteractionRepository.findAllInteractionsByUser(similarUser2, any())
            } returns Flux.just(
                UserInteraction(contentId3, InteractionType.SAVE),    // 1.5
                UserInteraction(contentId5, InteractionType.LIKE)     // 1.0
            )

            // Mock: contentRepository.findByIdsWithMetadata (언어 가중치 적용을 위해 필요)
            every {
                contentRepository.findByIdsWithMetadata(any())
            } answers {
                val ids = firstArg<List<UUID>>()
                Flux.fromIterable(ids.map { createContentWithMetadata(it, language = "en") })
            }

            // When: 추천 콘텐츠 조회
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10, "en")

            // Then: 점수 높은 순으로 정렬된 추천 콘텐츠 반환
            // Content3: 1.0 (LIKE) + 1.5 (SAVE) = 2.5
            // Content4: 2.0 (SHARE) = 2.0
            // Content5: 1.0 (LIKE) = 1.0
            StepVerifier.create(result.collectList())
                .assertNext { recommendedContents ->
                    assertThat(recommendedContents).hasSize(3)
                    assertThat(recommendedContents[0]).isEqualTo(contentId3)  // 점수 2.5
                    assertThat(recommendedContents[1]).isEqualTo(contentId4)  // 점수 2.0
                    assertThat(recommendedContents[2]).isEqualTo(contentId5)  // 점수 1.0
                }
                .verifyComplete()

            verify(exactly = 1) {
                userContentInteractionRepository.findAllInteractionsByUser(userId, any())
            }
            verify(exactly = 1) {
                userContentInteractionRepository.findUsersByContent(contentId1, null, any())
            }
            verify(exactly = 1) {
                userContentInteractionRepository.findUsersByContent(contentId2, null, any())
            }
        }

        @Test
        @DisplayName("가중치 테스트: SHARE > SAVE > LIKE 순으로 높은 점수를 받는다")
        fun getRecommendedContents_AppliesWeightsByInteractionType() {
            // Given: 나는 Content1을 좋아요
            val myInteractions = listOf(UserInteraction(contentId1, InteractionType.LIKE))

            every {
                userContentInteractionRepository.findAllInteractionsByUser(userId, any())
            } returns Flux.fromIterable(myInteractions)

            // Content1을 좋아한 유사 사용자
            every {
                userContentInteractionRepository.findUsersByContent(contentId1, null, any())
            } returns Flux.just(similarUser1)

            // similarUser1이 좋아한 콘텐츠: 각각 다른 인터랙션 타입
            every {
                userContentInteractionRepository.findAllInteractionsByUser(similarUser1, any())
            } returns Flux.just(
                UserInteraction(contentId2, InteractionType.LIKE),    // 1.0
                UserInteraction(contentId3, InteractionType.SAVE),    // 1.5
                UserInteraction(contentId4, InteractionType.SHARE)    // 2.0
            )

            // Mock: contentRepository.findByIdsWithMetadata
            every {
                contentRepository.findByIdsWithMetadata(any())
            } answers {
                val ids = firstArg<List<UUID>>()
                Flux.fromIterable(ids.map { createContentWithMetadata(it, language = "en") })
            }

            // When: 추천 콘텐츠 조회
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10, "en")

            // Then: SHARE > SAVE > LIKE 순으로 정렬
            StepVerifier.create(result.collectList())
                .assertNext { recommendedContents ->
                    assertThat(recommendedContents).hasSize(3)
                    assertThat(recommendedContents[0]).isEqualTo(contentId4)  // SHARE (2.0)
                    assertThat(recommendedContents[1]).isEqualTo(contentId3)  // SAVE (1.5)
                    assertThat(recommendedContents[2]).isEqualTo(contentId2)  // LIKE (1.0)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("중복 제거: 이미 내가 인터랙션한 콘텐츠는 추천하지 않는다")
        fun getRecommendedContents_ExcludesAlreadyInteractedContents() {
            // Given: 나는 Content1, Content2를 좋아요
            val myInteractions = listOf(
                UserInteraction(contentId1, InteractionType.LIKE),
                UserInteraction(contentId2, InteractionType.LIKE)
            )

            every {
                userContentInteractionRepository.findAllInteractionsByUser(userId, any())
            } returns Flux.fromIterable(myInteractions)

            // Content1을 좋아한 유사 사용자
            every {
                userContentInteractionRepository.findUsersByContent(contentId1, null, any())
            } returns Flux.just(similarUser1)

            every {
                userContentInteractionRepository.findUsersByContent(contentId2, null, any())
            } returns Flux.just(similarUser1)

            // similarUser1이 좋아한 콘텐츠: Content2(이미 봄), Content3(신규)
            every {
                userContentInteractionRepository.findAllInteractionsByUser(similarUser1, any())
            } returns Flux.just(
                UserInteraction(contentId2, InteractionType.LIKE),    // 이미 내가 봄 → 제외
                UserInteraction(contentId3, InteractionType.LIKE)     // 신규 → 추천
            )

            // Mock: contentRepository.findByIdsWithMetadata
            every {
                contentRepository.findByIdsWithMetadata(any())
            } answers {
                val ids = firstArg<List<UUID>>()
                Flux.fromIterable(ids.map { createContentWithMetadata(it, language = "en") })
            }

            // When: 추천 콘텐츠 조회
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10, "en")

            // Then: Content2는 제외되고 Content3만 추천
            StepVerifier.create(result.collectList())
                .assertNext { recommendedContents ->
                    assertThat(recommendedContents).hasSize(1)
                    assertThat(recommendedContents[0]).isEqualTo(contentId3)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("엣지 케이스: 신규 사용자(인터랙션 없음)는 빈 결과를 반환한다")
        fun getRecommendedContents_WithNewUser_ReturnsEmpty() {
            // Given: 신규 사용자 (인터랙션 없음)
            every {
                userContentInteractionRepository.findAllInteractionsByUser(userId, any())
            } returns Flux.empty()

            // When: 추천 콘텐츠 조회
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10, "en")

            // Then: 빈 결과 반환
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) {
                userContentInteractionRepository.findAllInteractionsByUser(userId, any())
            }
        }

        @Test
        @DisplayName("엣지 케이스: 유사 사용자가 없으면 빈 결과를 반환한다")
        fun getRecommendedContents_WithNoSimilarUsers_ReturnsEmpty() {
            // Given: 나는 Content1을 좋아요
            val myInteractions = listOf(UserInteraction(contentId1, InteractionType.LIKE))

            every {
                userContentInteractionRepository.findAllInteractionsByUser(userId, any())
            } returns Flux.fromIterable(myInteractions)

            // Content1을 좋아한 다른 사용자가 없음
            every {
                userContentInteractionRepository.findUsersByContent(contentId1, null, any())
            } returns Flux.empty()

            // When: 추천 콘텐츠 조회
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10, "en")

            // Then: 빈 결과 반환
            StepVerifier.create(result)
                .verifyComplete()
        }

        @Test
        @DisplayName("COMMENT 인터랙션은 점수 계산에 포함되지 않는다")
        fun getRecommendedContents_ExcludesCommentInteractions() {
            // Given: 나는 Content1을 좋아요
            val myInteractions = listOf(UserInteraction(contentId1, InteractionType.LIKE))

            every {
                userContentInteractionRepository.findAllInteractionsByUser(userId, any())
            } returns Flux.fromIterable(myInteractions)

            // Content1을 좋아한 유사 사용자
            every {
                userContentInteractionRepository.findUsersByContent(contentId1, null, any())
            } returns Flux.just(similarUser1)

            // similarUser1이 좋아한 콘텐츠: Content2(COMMENT), Content3(LIKE)
            every {
                userContentInteractionRepository.findAllInteractionsByUser(similarUser1, any())
            } returns Flux.just(
                UserInteraction(contentId2, InteractionType.COMMENT),  // 점수 0.0 → 제외
                UserInteraction(contentId3, InteractionType.LIKE)       // 점수 1.0
            )

            // Mock: contentRepository.findByIdsWithMetadata
            every {
                contentRepository.findByIdsWithMetadata(any())
            } answers {
                val ids = firstArg<List<UUID>>()
                Flux.fromIterable(ids.map { createContentWithMetadata(it, language = "en") })
            }

            // When: 추천 콘텐츠 조회
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10, "en")

            // Then: COMMENT는 제외되고 LIKE만 추천
            StepVerifier.create(result.collectList())
                .assertNext { recommendedContents ->
                    assertThat(recommendedContents).hasSize(1)
                    assertThat(recommendedContents[0]).isEqualTo(contentId3)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("시간 Decay 테스트: 같은 CF 점수일 때 최신 콘텐츠가 더 높은 순위에 있다")
        fun getRecommendedContents_AppliesTimeDecay_NewerContentRanksHigher() {
            // Given: 나는 Content1을 좋아요
            val myInteractions = listOf(UserInteraction(contentId1, InteractionType.LIKE))

            every {
                userContentInteractionRepository.findAllInteractionsByUser(userId, any())
            } returns Flux.fromIterable(myInteractions)

            // Content1을 좋아한 유사 사용자
            every {
                userContentInteractionRepository.findUsersByContent(contentId1, null, any())
            } returns Flux.just(similarUser1)

            // similarUser1이 좋아한 콘텐츠: Content2(LIKE), Content3(LIKE) - 동일한 CF 점수
            every {
                userContentInteractionRepository.findAllInteractionsByUser(similarUser1, any())
            } returns Flux.just(
                UserInteraction(contentId2, InteractionType.LIKE),  // 점수 1.0
                UserInteraction(contentId3, InteractionType.LIKE)   // 점수 1.0
            )

            val now = Instant.now()

            // Mock: Content2는 최신 (오늘), Content3는 30일 전
            every {
                contentRepository.findByIdsWithMetadata(any())
            } answers {
                val ids = firstArg<List<UUID>>()
                Flux.fromIterable(ids.map { id ->
                    when (id) {
                        contentId2 -> createContentWithMetadata(id, language = "en", createdAt = now)
                        contentId3 -> createContentWithMetadata(id, language = "en", createdAt = now.minus(30, ChronoUnit.DAYS))
                        else -> createContentWithMetadata(id, language = "en", createdAt = now)
                    }
                })
            }

            // When: 추천 콘텐츠 조회
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10, "en")

            // Then: 최신 콘텐츠(Content2)가 먼저 나와야 함
            // Content2: 1.0 * 1.0 (오늘 → decay=1.0) = 1.0
            // Content3: 1.0 * 0.55 (30일 전 → decay≈0.55) = 0.55
            StepVerifier.create(result.collectList())
                .assertNext { recommendedContents ->
                    assertThat(recommendedContents).hasSize(2)
                    assertThat(recommendedContents[0]).isEqualTo(contentId2)  // 최신 콘텐츠
                    assertThat(recommendedContents[1]).isEqualTo(contentId3)  // 오래된 콘텐츠
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("시간 Decay 테스트: 60일 경과 콘텐츠는 30% 정도의 점수만 유지")
        fun getRecommendedContents_AppliesTimeDecay_OldContentGetsReducedScore() {
            // Given: 나는 Content1을 좋아요
            val myInteractions = listOf(UserInteraction(contentId1, InteractionType.LIKE))

            every {
                userContentInteractionRepository.findAllInteractionsByUser(userId, any())
            } returns Flux.fromIterable(myInteractions)

            // Content1을 좋아한 유사 사용자
            every {
                userContentInteractionRepository.findUsersByContent(contentId1, null, any())
            } returns Flux.just(similarUser1)

            // similarUser1이 좋아한 콘텐츠:
            // Content2(LIKE) - 점수 1.0, 최신
            // Content3(SHARE) - 점수 2.0, 60일 전 → 2.0 * 0.30 = 0.6
            every {
                userContentInteractionRepository.findAllInteractionsByUser(similarUser1, any())
            } returns Flux.just(
                UserInteraction(contentId2, InteractionType.LIKE),   // 1.0
                UserInteraction(contentId3, InteractionType.SHARE)   // 2.0
            )

            val now = Instant.now()

            // Mock: Content2는 최신, Content3는 60일 전
            every {
                contentRepository.findByIdsWithMetadata(any())
            } answers {
                val ids = firstArg<List<UUID>>()
                Flux.fromIterable(ids.map { id ->
                    when (id) {
                        contentId2 -> createContentWithMetadata(id, language = "en", createdAt = now)
                        contentId3 -> createContentWithMetadata(id, language = "en", createdAt = now.minus(60, ChronoUnit.DAYS))
                        else -> createContentWithMetadata(id, language = "en", createdAt = now)
                    }
                })
            }

            // When: 추천 콘텐츠 조회
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10, "en")

            // Then: Content2(1.0)가 Content3(0.6)보다 높은 점수
            // Content2: 1.0 * 1.0 = 1.0
            // Content3: 2.0 * ~0.30 = ~0.6
            StepVerifier.create(result.collectList())
                .assertNext { recommendedContents ->
                    assertThat(recommendedContents).hasSize(2)
                    assertThat(recommendedContents[0]).isEqualTo(contentId2)  // LIKE이지만 최신
                    assertThat(recommendedContents[1]).isEqualTo(contentId3)  // SHARE지만 60일 전
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("시간 Decay 테스트: CF 완만한 감쇠율로 14일 경과해도 75% 유지")
        fun getRecommendedContents_AppliesTimeDecay_MildDecayFor14Days() {
            // Given: 나는 Content1을 좋아요
            val myInteractions = listOf(UserInteraction(contentId1, InteractionType.LIKE))

            every {
                userContentInteractionRepository.findAllInteractionsByUser(userId, any())
            } returns Flux.fromIterable(myInteractions)

            // Content1을 좋아한 유사 사용자
            every {
                userContentInteractionRepository.findUsersByContent(contentId1, null, any())
            } returns Flux.just(similarUser1)

            // similarUser1이 좋아한 콘텐츠:
            // Content2(LIKE) - 점수 1.0, 최신
            // Content3(SAVE) - 점수 1.5, 14일 전 → 1.5 * 0.75 = 1.125 > 1.0
            every {
                userContentInteractionRepository.findAllInteractionsByUser(similarUser1, any())
            } returns Flux.just(
                UserInteraction(contentId2, InteractionType.LIKE),   // 1.0
                UserInteraction(contentId3, InteractionType.SAVE)    // 1.5
            )

            val now = Instant.now()

            // Mock: Content2는 최신, Content3는 14일 전
            every {
                contentRepository.findByIdsWithMetadata(any())
            } answers {
                val ids = firstArg<List<UUID>>()
                Flux.fromIterable(ids.map { id ->
                    when (id) {
                        contentId2 -> createContentWithMetadata(id, language = "en", createdAt = now)
                        contentId3 -> createContentWithMetadata(id, language = "en", createdAt = now.minus(14, ChronoUnit.DAYS))
                        else -> createContentWithMetadata(id, language = "en", createdAt = now)
                    }
                })
            }

            // When: 추천 콘텐츠 조회
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10, "en")

            // Then: Content3(1.125)가 Content2(1.0)보다 높은 점수
            // CF는 완만한 감쇠율(0.02)이므로 14일 후에도 75% 유지
            // Content2: 1.0 * 1.0 = 1.0
            // Content3: 1.5 * ~0.75 = ~1.125
            StepVerifier.create(result.collectList())
                .assertNext { recommendedContents ->
                    assertThat(recommendedContents).hasSize(2)
                    assertThat(recommendedContents[0]).isEqualTo(contentId3)  // SAVE + 14일 전 = 1.125
                    assertThat(recommendedContents[1]).isEqualTo(contentId2)  // LIKE + 최신 = 1.0
                }
                .verifyComplete()
        }
    }
}
