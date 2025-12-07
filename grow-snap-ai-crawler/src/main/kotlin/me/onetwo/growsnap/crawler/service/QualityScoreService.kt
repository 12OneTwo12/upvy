package me.onetwo.growsnap.crawler.service

import me.onetwo.growsnap.crawler.domain.AiContentJob
import me.onetwo.growsnap.crawler.domain.QualityScore
import me.onetwo.growsnap.crawler.domain.ReviewPriority
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 품질 점수 계산 서비스 인터페이스
 */
interface QualityScoreService {

    /**
     * 콘텐츠 품질 점수 계산
     *
     * @param job AI 콘텐츠 작업
     * @return 품질 점수
     */
    fun calculateScore(job: AiContentJob): QualityScore

    /**
     * 검토 우선순위 결정
     *
     * @param totalScore 총 점수
     * @return 검토 우선순위
     */
    fun determineReviewPriority(totalScore: Int): ReviewPriority

    /**
     * 자동 승인 여부 결정
     *
     * @param totalScore 총 점수
     * @return true: 승인 대기열로, false: 자동 거절
     */
    fun shouldProceedToApproval(totalScore: Int): Boolean
}

/**
 * 품질 점수 계산 서비스 구현체
 */
@Service
class QualityScoreServiceImpl(
    @Value("\${quality.min-score:70}") private val minScore: Int
) : QualityScoreService {

    companion object {
        private val logger = LoggerFactory.getLogger(QualityScoreServiceImpl::class.java)
        private const val HIGH_PRIORITY_THRESHOLD = 85
    }

    override fun calculateScore(job: AiContentJob): QualityScore {
        logger.debug("품질 점수 계산 시작: jobId={}", job.id)

        // 1. 콘텐츠 관련성 (0-25)
        val contentRelevance = calculateContentRelevance(job)

        // 2. 오디오 명확성 (0-25) - 자막 길이와 품질로 추정
        val audioClarity = calculateAudioClarity(job)

        // 3. 영상 품질 (0-25) - 편집 여부와 메타데이터로 추정
        val visualQuality = calculateVisualQuality(job)

        // 4. 교육적 가치 (0-25) - LLM 평가 점수와 카테고리로 추정
        val educationalValue = calculateEducationalValue(job)

        val totalScore = contentRelevance + audioClarity + visualQuality + educationalValue

        val qualityScore = QualityScore(
            totalScore = totalScore,
            contentRelevance = contentRelevance,
            audioClarity = audioClarity,
            visualQuality = visualQuality,
            educationalValue = educationalValue
        )

        logger.info(
            "품질 점수 계산 완료: jobId={}, totalScore={}, relevance={}, audio={}, visual={}, educational={}",
            job.id, totalScore, contentRelevance, audioClarity, visualQuality, educationalValue
        )

        return qualityScore
    }

    override fun determineReviewPriority(totalScore: Int): ReviewPriority {
        return when {
            totalScore >= HIGH_PRIORITY_THRESHOLD -> ReviewPriority.HIGH
            totalScore >= minScore -> ReviewPriority.NORMAL
            else -> ReviewPriority.LOW
        }
    }

    override fun shouldProceedToApproval(totalScore: Int): Boolean {
        return totalScore >= minScore
    }

    /**
     * 콘텐츠 관련성 점수 계산 (0-25)
     */
    private fun calculateContentRelevance(job: AiContentJob): Int {
        var score = 0

        // 제목이 있으면 +5
        if (!job.generatedTitle.isNullOrBlank()) score += 5

        // 설명이 있으면 +5
        if (!job.generatedDescription.isNullOrBlank()) score += 5

        // 태그가 있으면 +5
        if (!job.generatedTags.isNullOrBlank()) score += 5

        // 카테고리가 있으면 +5
        if (!job.category.isNullOrBlank()) score += 5

        // 난이도가 있으면 +5
        if (job.difficulty != null) score += 5

        return score.coerceAtMost(25)
    }

    /**
     * 오디오 명확성 점수 계산 (0-25)
     */
    private fun calculateAudioClarity(job: AiContentJob): Int {
        val transcript = job.transcript ?: return 0

        // 자막 길이에 따른 점수 (적당한 길이가 좋음)
        val length = transcript.length
        val lengthScore = when {
            length < 500 -> 10   // 너무 짧음
            length < 2000 -> 20 // 적당함
            length < 5000 -> 25 // 좋음
            length < 10000 -> 20 // 약간 길음
            else -> 15           // 너무 길음
        }

        return lengthScore
    }

    /**
     * 영상 품질 점수 계산 (0-25)
     */
    private fun calculateVisualQuality(job: AiContentJob): Int {
        var score = 10  // 기본 점수

        // 편집된 비디오가 있으면 +10
        if (!job.editedVideoS3Key.isNullOrBlank()) score += 10

        // 썸네일이 있으면 +5
        if (!job.thumbnailS3Key.isNullOrBlank()) score += 5

        return score.coerceAtMost(25)
    }

    /**
     * 교육적 가치 점수 계산 (0-25)
     */
    private fun calculateEducationalValue(job: AiContentJob): Int {
        var score = 0

        // 기존 품질 점수가 있으면 활용
        val existingScore = job.qualityScore
        if (existingScore != null) {
            // LLM 사전 평가 점수를 25점 만점으로 변환
            score = (existingScore * 0.25).toInt()
        } else {
            // 기본 점수
            score = 15
        }

        // 카테고리가 교육적인 경우 보너스
        val educationalCategories = listOf(
            "프로그래밍", "과학", "수학", "언어", "역사",
            "PROGRAMMING", "SCIENCE", "MATH", "LANGUAGE", "HISTORY"
        )
        if (job.category in educationalCategories) {
            score += 5
        }

        return score.coerceAtMost(25)
    }
}
