package me.onetwo.upvy.crawler.service

import me.onetwo.upvy.crawler.domain.AiContentJob
import me.onetwo.upvy.crawler.domain.Difficulty
import me.onetwo.upvy.crawler.domain.JobStatus
import me.onetwo.upvy.crawler.domain.ReviewPriority
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("QualityScoreService 테스트")
class QualityScoreServiceTest {

    private lateinit var service: QualityScoreServiceImpl

    @BeforeEach
    fun setUp() {
        service = QualityScoreServiceImpl(minScore = 70)
    }

    @Nested
    @DisplayName("calculateScore - 품질 점수 계산")
    inner class CalculateScore {

        @Test
        @DisplayName("모든 메타데이터가 있는 Job은 높은 점수를 받는다")
        fun calculateScore_WithAllMetadata_ReturnsHighScore() {
            // Given: 모든 메타데이터가 있는 Job
            val job = AiContentJob(
                id = 1L,
                youtubeVideoId = "complete_job",
                transcript = "적당한 길이의 자막 텍스트입니다. ".repeat(100),
                generatedTitle = "완전한 콘텐츠",
                generatedDescription = "모든 정보가 있는 콘텐츠입니다.",
                generatedTags = "[\"tag1\", \"tag2\", \"tag3\"]",
                category = "PROGRAMMING",
                difficulty = Difficulty.INTERMEDIATE,
                editedVideoS3Key = "clips/complete/1.mp4",
                thumbnailS3Key = "thumbnails/complete/1.jpg",
                status = JobStatus.EDITED,
                qualityScore = 85
            )

            // When: 점수 계산
            val score = service.calculateScore(job)

            // Then: 높은 점수
            assertThat(score.totalScore).isGreaterThanOrEqualTo(70)
            assertThat(score.contentRelevance).isEqualTo(25)  // 모든 메타데이터
            assertThat(score.visualQuality).isEqualTo(25)    // 편집됨 + 썸네일
        }

        @Test
        @DisplayName("메타데이터가 부족한 Job은 낮은 점수를 받는다")
        fun calculateScore_WithMissingMetadata_ReturnsLowScore() {
            // Given: 메타데이터가 부족한 Job
            val job = AiContentJob(
                id = 2L,
                youtubeVideoId = "incomplete_job",
                transcript = "짧음",
                generatedTitle = null,
                generatedDescription = null,
                generatedTags = null,
                category = null,
                difficulty = null,
                editedVideoS3Key = null,
                thumbnailS3Key = null,
                status = JobStatus.EDITED
            )

            // When: 점수 계산
            val score = service.calculateScore(job)

            // Then: 낮은 점수
            assertThat(score.totalScore).isLessThan(70)
            assertThat(score.contentRelevance).isEqualTo(0)  // 메타데이터 없음
            assertThat(score.visualQuality).isEqualTo(10)    // 기본 점수만
        }
    }

    @Nested
    @DisplayName("determineReviewPriority - 검토 우선순위 결정")
    inner class DetermineReviewPriority {

        @Test
        @DisplayName("85점 이상이면 HIGH 우선순위가 된다")
        fun determineReviewPriority_With85OrMore_ReturnsHigh() {
            // Given: 85점 이상
            val score = 90

            // When: 우선순위 결정
            val priority = service.determineReviewPriority(score)

            // Then: HIGH
            assertThat(priority).isEqualTo(ReviewPriority.HIGH)
        }

        @Test
        @DisplayName("70-84점이면 NORMAL 우선순위가 된다")
        fun determineReviewPriority_With70To84_ReturnsNormal() {
            // Given: 70-84점
            val scores = listOf(70, 75, 80, 84)

            scores.forEach { score ->
                // When: 우선순위 결정
                val priority = service.determineReviewPriority(score)

                // Then: NORMAL
                assertThat(priority).isEqualTo(ReviewPriority.NORMAL)
            }
        }

        @Test
        @DisplayName("70점 미만이면 LOW 우선순위가 된다")
        fun determineReviewPriority_WithLessThan70_ReturnsLow() {
            // Given: 70점 미만
            val score = 50

            // When: 우선순위 결정
            val priority = service.determineReviewPriority(score)

            // Then: LOW
            assertThat(priority).isEqualTo(ReviewPriority.LOW)
        }
    }

    @Nested
    @DisplayName("shouldProceedToApproval - 승인 진행 여부")
    inner class ShouldProceedToApproval {

        @Test
        @DisplayName("70점 이상이면 승인 대기열로 진행한다")
        fun shouldProceedToApproval_With70OrMore_ReturnsTrue() {
            // Given: 70점 이상
            val scores = listOf(70, 85, 100)

            scores.forEach { score ->
                // When: 승인 진행 여부 결정
                val shouldProceed = service.shouldProceedToApproval(score)

                // Then: true
                assertThat(shouldProceed).isTrue()
            }
        }

        @Test
        @DisplayName("70점 미만이면 자동 거절된다")
        fun shouldProceedToApproval_WithLessThan70_ReturnsFalse() {
            // Given: 70점 미만
            val scores = listOf(0, 50, 69)

            scores.forEach { score ->
                // When: 승인 진행 여부 결정
                val shouldProceed = service.shouldProceedToApproval(score)

                // Then: false
                assertThat(shouldProceed).isFalse()
            }
        }
    }
}
