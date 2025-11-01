package me.onetwo.growsnap.domain.analytics.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.repository.UserContentInteractionRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 사용자별 콘텐츠 인터랙션 서비스 단위 테스트
 *
 * Repository의 동작을 모킹하여 Service 계층의 비즈니스 로직만 검증합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("UserContentInteractionService 단위 테스트")
class UserContentInteractionServiceImplTest {

    @MockK
    private lateinit var userContentInteractionRepository: UserContentInteractionRepository

    @InjectMockKs
    private lateinit var userContentInteractionService: UserContentInteractionServiceImpl

    private val testUserId = UUID.randomUUID()
    private val testContentId = UUID.randomUUID()

    @Nested
    @DisplayName("saveUserInteraction - 사용자 인터랙션 저장")
    inner class SaveUserInteraction {

        @Test
        @DisplayName("LIKE 인터랙션을 저장하면, Repository에 저장하고 성공 로그를 출력한다")
        fun saveUserInteraction_WithLikeType_SavesSuccessfully() {
            // Given: LIKE 인터랙션
            val interactionType = InteractionType.LIKE
            every {
                userContentInteractionRepository.save(testUserId, testContentId, interactionType)
            } returns Mono.empty()

            // When: 인터랙션 저장
            userContentInteractionService.saveUserInteraction(testUserId, testContentId, interactionType)

            // Then: Repository save 호출 확인
            // subscribe()가 비동기로 실행되므로 약간의 대기 시간 필요
            Thread.sleep(100)

            verify(exactly = 1) {
                userContentInteractionRepository.save(testUserId, testContentId, interactionType)
            }
        }

        @Test
        @DisplayName("SAVE 인터랙션을 저장하면, Repository에 저장하고 성공 로그를 출력한다")
        fun saveUserInteraction_WithSaveType_SavesSuccessfully() {
            // Given: SAVE 인터랙션
            val interactionType = InteractionType.SAVE
            every {
                userContentInteractionRepository.save(testUserId, testContentId, interactionType)
            } returns Mono.empty()

            // When: 인터랙션 저장
            userContentInteractionService.saveUserInteraction(testUserId, testContentId, interactionType)

            // Then: Repository save 호출 확인
            Thread.sleep(100)

            verify(exactly = 1) {
                userContentInteractionRepository.save(testUserId, testContentId, interactionType)
            }
        }

        @Test
        @DisplayName("SHARE 인터랙션을 저장하면, Repository에 저장하고 성공 로그를 출력한다")
        fun saveUserInteraction_WithShareType_SavesSuccessfully() {
            // Given: SHARE 인터랙션
            val interactionType = InteractionType.SHARE
            every {
                userContentInteractionRepository.save(testUserId, testContentId, interactionType)
            } returns Mono.empty()

            // When: 인터랙션 저장
            userContentInteractionService.saveUserInteraction(testUserId, testContentId, interactionType)

            // Then: Repository save 호출 확인
            Thread.sleep(100)

            verify(exactly = 1) {
                userContentInteractionRepository.save(testUserId, testContentId, interactionType)
            }
        }

        @Test
        @DisplayName("COMMENT 인터랙션을 저장하면, Repository에 저장하고 성공 로그를 출력한다")
        fun saveUserInteraction_WithCommentType_SavesSuccessfully() {
            // Given: COMMENT 인터랙션
            val interactionType = InteractionType.COMMENT
            every {
                userContentInteractionRepository.save(testUserId, testContentId, interactionType)
            } returns Mono.empty()

            // When: 인터랙션 저장
            userContentInteractionService.saveUserInteraction(testUserId, testContentId, interactionType)

            // Then: Repository save 호출 확인
            Thread.sleep(100)

            verify(exactly = 1) {
                userContentInteractionRepository.save(testUserId, testContentId, interactionType)
            }
        }

        @Test
        @DisplayName("Repository 저장 실패 시, 에러 로그를 출력한다")
        fun saveUserInteraction_WhenRepositoryFails_LogsError() {
            // Given: Repository에서 에러 발생
            val interactionType = InteractionType.LIKE
            val error = RuntimeException("Database connection failed")
            every {
                userContentInteractionRepository.save(testUserId, testContentId, interactionType)
            } returns Mono.error(error)

            // When: 인터랙션 저장 시도
            userContentInteractionService.saveUserInteraction(testUserId, testContentId, interactionType)

            // Then: Repository save 호출 확인 (에러 발생해도 호출은 됨)
            Thread.sleep(100)

            verify(exactly = 1) {
                userContentInteractionRepository.save(testUserId, testContentId, interactionType)
            }
        }

        @Test
        @DisplayName("여러 인터랙션을 연속으로 저장하면, 모두 Repository에 저장된다")
        fun saveUserInteraction_MultipleTimes_SavesAll() {
            // Given: 여러 인터랙션
            val interactions = listOf(
                InteractionType.LIKE,
                InteractionType.SAVE,
                InteractionType.COMMENT
            )

            interactions.forEach { type ->
                every {
                    userContentInteractionRepository.save(testUserId, testContentId, type)
                } returns Mono.empty()
            }

            // When: 여러 번 저장
            interactions.forEach { type ->
                userContentInteractionService.saveUserInteraction(testUserId, testContentId, type)
            }

            // Then: 모든 인터랙션이 저장됨
            Thread.sleep(300)

            interactions.forEach { type ->
                verify(exactly = 1) {
                    userContentInteractionRepository.save(testUserId, testContentId, type)
                }
            }
        }

        @Test
        @DisplayName("다른 사용자와 콘텐츠에 대한 인터랙션을 구분하여 저장한다")
        fun saveUserInteraction_DifferentUsersAndContents_SavesSeparately() {
            // Given: 다른 사용자와 콘텐츠
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val contentId1 = UUID.randomUUID()
            val contentId2 = UUID.randomUUID()
            val interactionType = InteractionType.LIKE

            every {
                userContentInteractionRepository.save(userId1, contentId1, interactionType)
            } returns Mono.empty()

            every {
                userContentInteractionRepository.save(userId2, contentId2, interactionType)
            } returns Mono.empty()

            // When: 각각 저장
            userContentInteractionService.saveUserInteraction(userId1, contentId1, interactionType)
            userContentInteractionService.saveUserInteraction(userId2, contentId2, interactionType)

            // Then: 각각 호출됨
            Thread.sleep(200)

            verify(exactly = 1) {
                userContentInteractionRepository.save(userId1, contentId1, interactionType)
            }

            verify(exactly = 1) {
                userContentInteractionRepository.save(userId2, contentId2, interactionType)
            }
        }
    }
}
