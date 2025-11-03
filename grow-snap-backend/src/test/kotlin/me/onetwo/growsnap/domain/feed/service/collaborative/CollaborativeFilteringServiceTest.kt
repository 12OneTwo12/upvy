package me.onetwo.growsnap.domain.feed.service.collaborative

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.dto.UserInteraction
import me.onetwo.growsnap.domain.analytics.repository.UserContentInteractionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
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
class CollaborativeFilteringServiceTest {

    private lateinit var userContentInteractionRepository: UserContentInteractionRepository
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
        collaborativeFilteringService = CollaborativeFilteringServiceImpl(
            userContentInteractionRepository
        )
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

            // When: 추천 콘텐츠 조회
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10)

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

            // When: 추천 콘텐츠 조회
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10)

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

            // When: 추천 콘텐츠 조회
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10)

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
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10)

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
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10)

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

            // When: 추천 콘텐츠 조회
            val result = collaborativeFilteringService.getRecommendedContents(userId, 10)

            // Then: COMMENT는 제외되고 LIKE만 추천
            StepVerifier.create(result.collectList())
                .assertNext { recommendedContents ->
                    assertThat(recommendedContents).hasSize(1)
                    assertThat(recommendedContents[0]).isEqualTo(contentId3)
                }
                .verifyComplete()
        }
    }
}
