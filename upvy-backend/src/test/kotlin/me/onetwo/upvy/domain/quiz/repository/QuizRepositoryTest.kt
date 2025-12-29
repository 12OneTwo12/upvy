package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.content.model.Content
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.domain.content.repository.ContentRepository
import me.onetwo.upvy.domain.quiz.exception.QuizException
import me.onetwo.upvy.domain.quiz.model.Quiz
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.domain.user.model.UserStatus
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import java.util.UUID

/**
 * QuizRepository 통합 테스트
 *
 * 실제 H2 데이터베이스를 사용하여 Repository 계층을 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("QuizRepository 통합 테스트")
class QuizRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var quizRepository: QuizRepository

    @Autowired
    private lateinit var contentRepository: ContentRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var testUser: User
    private lateinit var testContent: Content

    @BeforeEach
    fun setUp() {
        // 테스트용 사용자 생성
        testUser = userRepository.save(
            User(
                email = "quiz-test@example.com",
                role = UserRole.USER,
                status = UserStatus.ACTIVE
            )
        ).block()!!

        // 테스트용 콘텐츠 생성 (QUIZ 타입)
        testContent = contentRepository.save(
            Content(
                creatorId = testUser.id!!,
                contentType = ContentType.QUIZ,
                url = "https://example.com/quiz",
                thumbnailUrl = "https://example.com/thumbnail.jpg",
                width = 1080,
                height = 1920,
                createdBy = testUser.id.toString()
            )
        ).block()!!
    }

    @Nested
    @DisplayName("save - 퀴즈 저장")
    inner class Save {

        @Test
        @DisplayName("퀴즈를 저장하면, UUID가 부여된다")
        fun save_WithValidQuiz_ReturnsQuizWithId() {
            // Given: 새로운 퀴즈
            val quiz = Quiz(
                contentId = testContent.id!!,
                question = "Kotlin에서 val과 var의 차이는?",
                allowMultipleAnswers = false,
                createdBy = testUser.id.toString(),
                updatedBy = testUser.id.toString()
            )

            // When: 저장
            val savedQuiz = quizRepository.save(quiz).block()!!

            // Then: UUID가 부여됨
            assertThat(savedQuiz.id).isNotNull()
            assertThat(savedQuiz.contentId).isEqualTo(testContent.id)
            assertThat(savedQuiz.question).isEqualTo("Kotlin에서 val과 var의 차이는?")
            assertThat(savedQuiz.allowMultipleAnswers).isFalse()
        }
    }

    @Nested
    @DisplayName("findById - ID로 퀴즈 조회")
    inner class FindById {

        @Test
        @DisplayName("존재하는 퀴즈 ID로 조회하면, 퀴즈를 반환한다")
        fun findById_WhenQuizExists_ReturnsQuiz() {
            // Given: 퀴즈 저장
            val savedQuiz = quizRepository.save(
                Quiz(
                    contentId = testContent.id!!,
                    question = "Test Question",
                    allowMultipleAnswers = false,
                    createdBy = testUser.id.toString(),
                    updatedBy = testUser.id.toString()
                )
            ).block()!!

            // When: ID로 조회
            val foundQuiz = quizRepository.findById(savedQuiz.id!!).block()

            // Then: 퀴즈가 조회됨
            assertThat(foundQuiz).isNotNull
            assertThat(foundQuiz!!.id).isEqualTo(savedQuiz.id)
            assertThat(foundQuiz.question).isEqualTo("Test Question")
        }

        @Test
        @DisplayName("존재하지 않는 퀴즈 ID로 조회하면, 예외가 발생한다")
        fun findById_WhenQuizNotExists_ThrowsException() {
            // Given: 존재하지 않는 ID
            val nonExistingId = UUID.randomUUID()

            // When & Then: 예외 발생
            val result = quizRepository.findById(nonExistingId)
            StepVerifier.create(result)
                .expectError(QuizException.QuizNotFoundException::class.java)
                .verify()
        }

        @Test
        @DisplayName("삭제된 퀴즈 ID로 조회하면, 예외가 발생한다 (Soft Delete)")
        fun findById_WhenQuizDeleted_ThrowsException() {
            // Given: 퀴즈 저장 후 삭제
            val savedQuiz = quizRepository.save(
                Quiz(
                    contentId = testContent.id!!,
                    question = "Deleted Quiz",
                    allowMultipleAnswers = false,
                    createdBy = testUser.id.toString(),
                    updatedBy = testUser.id.toString()
                )
            ).block()!!

            quizRepository.delete(savedQuiz.id!!, testUser.id!!).block()

            // When & Then: 예외 발생 (soft delete)
            val result = quizRepository.findById(savedQuiz.id!!)
            StepVerifier.create(result)
                .expectError(QuizException.QuizNotFoundException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("findByContentId - 콘텐츠 ID로 퀴즈 조회")
    inner class FindByContentId {

        @Test
        @DisplayName("콘텐츠 ID로 퀴즈를 조회하면, 퀴즈를 반환한다")
        fun findByContentId_WhenQuizExists_ReturnsQuiz() {
            // Given: 퀴즈 저장
            val savedQuiz = quizRepository.save(
                Quiz(
                    contentId = testContent.id!!,
                    question = "Content Quiz",
                    allowMultipleAnswers = true,
                    createdBy = testUser.id.toString(),
                    updatedBy = testUser.id.toString()
                )
            ).block()!!

            // When: 콘텐츠 ID로 조회
            val foundQuiz = quizRepository.findByContentId(testContent.id!!).block()

            // Then: 퀴즈가 조회됨
            assertThat(foundQuiz).isNotNull
            assertThat(foundQuiz!!.id).isEqualTo(savedQuiz.id)
            assertThat(foundQuiz.contentId).isEqualTo(testContent.id)
            assertThat(foundQuiz.allowMultipleAnswers).isTrue()
        }

        @Test
        @DisplayName("콘텐츠에 퀴즈가 없으면, 예외가 발생한다")
        fun findByContentId_WhenQuizNotExists_ThrowsException() {
            // Given: 퀴즈가 없는 새로운 콘텐츠
            val newContent = contentRepository.save(
                Content(
                    creatorId = testUser.id!!,
                    contentType = ContentType.QUIZ,
                    url = "https://example.com/quiz2",
                    thumbnailUrl = "https://example.com/thumbnail2.jpg",
                    width = 1080,
                    height = 1920,
                    createdBy = testUser.id.toString()
                )
            ).block()!!

            // When & Then: 예외 발생
            val result = quizRepository.findByContentId(newContent.id!!)
            StepVerifier.create(result)
                .expectError(QuizException.QuizNotFoundException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("update - 퀴즈 수정")
    inner class Update {

        @Test
        @DisplayName("퀴즈를 수정하면, 수정된 퀴즈를 반환한다")
        fun update_WithValidQuiz_ReturnsUpdatedQuiz() {
            // Given: 기존 퀴즈
            val savedQuiz = quizRepository.save(
                Quiz(
                    contentId = testContent.id!!,
                    question = "Original Question",
                    allowMultipleAnswers = false,
                    createdBy = testUser.id.toString(),
                    updatedBy = testUser.id.toString()
                )
            ).block()!!

            // When: 퀴즈 수정
            val updatedQuiz = savedQuiz.copy(
                question = "Updated Question",
                allowMultipleAnswers = true,
                updatedBy = testUser.id.toString()
            )
            val result = quizRepository.update(updatedQuiz).block()!!

            // Then: 수정된 내용 반영
            val foundQuiz = quizRepository.findById(savedQuiz.id!!).block()!!
            assertThat(foundQuiz.question).isEqualTo("Updated Question")
            assertThat(foundQuiz.allowMultipleAnswers).isTrue()
        }
    }

    @Nested
    @DisplayName("delete - 퀴즈 삭제 (Soft Delete)")
    inner class Delete {

        @Test
        @DisplayName("퀴즈를 삭제하면, deletedAt이 설정된다")
        fun delete_WithExistingQuiz_SetsDeletedAt() {
            // Given: 기존 퀴즈
            val savedQuiz = quizRepository.save(
                Quiz(
                    contentId = testContent.id!!,
                    question = "To Be Deleted",
                    allowMultipleAnswers = false,
                    createdBy = testUser.id.toString(),
                    updatedBy = testUser.id.toString()
                )
            ).block()!!

            // When: 삭제
            quizRepository.delete(savedQuiz.id!!, testUser.id!!).block()

            // Then: soft delete되어 조회 시 예외 발생
            val result = quizRepository.findById(savedQuiz.id!!)
            StepVerifier.create(result)
                .expectError(QuizException.QuizNotFoundException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("existsByContentId - 콘텐츠에 퀴즈 존재 여부 확인")
    inner class ExistsByContentId {

        @Test
        @DisplayName("콘텐츠에 퀴즈가 있으면, true를 반환한다")
        fun existsByContentId_WhenQuizExists_ReturnsTrue() {
            // Given: 퀴즈 저장
            quizRepository.save(
                Quiz(
                    contentId = testContent.id!!,
                    question = "Exists Test",
                    allowMultipleAnswers = false,
                    createdBy = testUser.id.toString(),
                    updatedBy = testUser.id.toString()
                )
            ).block()

            // When: 존재 여부 확인
            val exists = quizRepository.existsByContentId(testContent.id!!).block()!!

            // Then: true
            assertThat(exists).isTrue()
        }

        @Test
        @DisplayName("콘텐츠에 퀴즈가 없으면, false를 반환한다")
        fun existsByContentId_WhenQuizNotExists_ReturnsFalse() {
            // Given: 퀴즈가 없는 새로운 콘텐츠
            val newContent = contentRepository.save(
                Content(
                    creatorId = testUser.id!!,
                    contentType = ContentType.QUIZ,
                    url = "https://example.com/quiz3",
                    thumbnailUrl = "https://example.com/thumbnail3.jpg",
                    width = 1080,
                    height = 1920,
                    createdBy = testUser.id.toString()
                )
            ).block()!!

            // When: 존재 여부 확인
            val exists = quizRepository.existsByContentId(newContent.id!!).block()!!

            // Then: false
            assertThat(exists).isFalse()
        }
    }
}
