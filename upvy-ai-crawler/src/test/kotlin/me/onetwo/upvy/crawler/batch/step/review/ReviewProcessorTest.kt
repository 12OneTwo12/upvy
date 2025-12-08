package me.onetwo.upvy.crawler.batch.step.review

import me.onetwo.upvy.crawler.domain.AiContentJob
import me.onetwo.upvy.crawler.domain.Difficulty
import me.onetwo.upvy.crawler.domain.JobStatus
import me.onetwo.upvy.crawler.service.QualityScoreServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ReviewProcessor 테스트")
class ReviewProcessorTest {

    private lateinit var qualityScoreService: QualityScoreServiceImpl
    private lateinit var processor: ReviewProcessor

    @BeforeEach
    fun setUp() {
        qualityScoreService = QualityScoreServiceImpl(minScore = 70)
        processor = ReviewProcessor(qualityScoreService)
    }

    @Nested
    @DisplayName("process - 품질 검토 및 상태 전환")
    inner class Process {

        @Test
        @DisplayName("품질 점수가 70점 이상이면 PENDING_APPROVAL 상태가 된다")
        fun process_WithHighQualityScore_BecomePendingApproval() {
            // Given: 고품질 콘텐츠 (모든 메타데이터 있음)
            val job = AiContentJob(
                id = 1L,
                youtubeVideoId = "high_quality_123",
                youtubeTitle = "High Quality Video",
                transcript = "이것은 충분히 긴 자막 텍스트입니다. 품질 점수 계산에 사용됩니다. ".repeat(50),
                generatedTitle = "고품질 교육 콘텐츠",
                generatedDescription = "AI가 생성한 설명입니다.",
                generatedTags = "[\"tag1\", \"tag2\"]",
                category = "PROGRAMMING",
                difficulty = Difficulty.BEGINNER,
                editedVideoS3Key = "clips/high_quality_123/1.mp4",
                thumbnailS3Key = "thumbnails/high_quality_123/1.jpg",
                status = JobStatus.EDITED,
                qualityScore = 85
            )

            // When: 프로세서 실행
            val result = processor.process(job)

            // Then: PENDING_APPROVAL 상태
            assertThat(result).isNotNull
            assertThat(result!!.status).isEqualTo(JobStatus.PENDING_APPROVAL)
            assertThat(result.qualityScore).isGreaterThanOrEqualTo(70)
        }

        @Test
        @DisplayName("품질 점수가 70점 미만이면 REJECTED 상태가 된다")
        fun process_WithLowQualityScore_BecomeRejected() {
            // Given: 저품질 콘텐츠 (메타데이터 부족)
            val job = AiContentJob(
                id = 2L,
                youtubeVideoId = "low_quality_123",
                youtubeTitle = "Low Quality Video",
                transcript = "짧은 자막",  // 너무 짧음
                generatedTitle = null,      // 제목 없음
                generatedDescription = null, // 설명 없음
                generatedTags = null,        // 태그 없음
                category = null,             // 카테고리 없음
                difficulty = null,           // 난이도 없음
                editedVideoS3Key = null,     // 편집 안됨
                thumbnailS3Key = null,       // 썸네일 없음
                status = JobStatus.EDITED,
                qualityScore = 30
            )

            // When: 프로세서 실행
            val result = processor.process(job)

            // Then: REJECTED 상태
            assertThat(result).isNotNull
            assertThat(result!!.status).isEqualTo(JobStatus.REJECTED)
            assertThat(result.qualityScore).isLessThan(70)
        }

        @Test
        @DisplayName("중간 품질 콘텐츠는 PENDING_APPROVAL 상태가 된다")
        fun process_WithMediumQualityScore_BecomePendingApproval() {
            // Given: 중간 품질 콘텐츠
            val job = AiContentJob(
                id = 3L,
                youtubeVideoId = "medium_quality_123",
                youtubeTitle = "Medium Quality Video",
                transcript = "적당한 길이의 자막 텍스트입니다. ".repeat(100),
                generatedTitle = "중간 품질 콘텐츠",
                generatedDescription = "설명입니다.",
                generatedTags = "[\"tag1\"]",
                category = "LIFESTYLE",
                difficulty = Difficulty.INTERMEDIATE,
                editedVideoS3Key = "clips/medium/1.mp4",
                thumbnailS3Key = null,  // 썸네일 없음
                status = JobStatus.EDITED,
                qualityScore = 75
            )

            // When: 프로세서 실행
            val result = processor.process(job)

            // Then: PENDING_APPROVAL 상태 (70점 이상)
            assertThat(result).isNotNull
            assertThat(result!!.status).isEqualTo(JobStatus.PENDING_APPROVAL)
        }
    }
}
