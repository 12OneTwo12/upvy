package me.onetwo.growsnap.crawler.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * AI 콘텐츠 작업 Repository
 */
@Repository
interface AiContentJobRepository : JpaRepository<AiContentJob, Long> {

    /**
     * 상태별 작업 조회 (Soft Delete 제외)
     */
    @Query("SELECT j FROM AiContentJob j WHERE j.status = :status AND j.deletedAt IS NULL")
    fun findByStatus(status: JobStatus): List<AiContentJob>

    /**
     * YouTube 비디오 ID로 작업 조회 (Soft Delete 제외)
     */
    @Query("SELECT j FROM AiContentJob j WHERE j.youtubeVideoId = :videoId AND j.deletedAt IS NULL")
    fun findByYoutubeVideoId(videoId: String): AiContentJob?

    /**
     * 승인 대기 중인 작업 목록 조회
     */
    @Query("SELECT j FROM AiContentJob j WHERE j.status = 'PENDING_APPROVAL' AND j.deletedAt IS NULL ORDER BY j.createdAt DESC")
    fun findPendingApproval(): List<AiContentJob>

    /**
     * 품질 점수가 기준 이상인 작업 조회
     */
    @Query("SELECT j FROM AiContentJob j WHERE j.qualityScore >= :minScore AND j.status = :status AND j.deletedAt IS NULL")
    fun findByQualityScoreGreaterThanEqualAndStatus(minScore: Int, status: JobStatus): List<AiContentJob>
}
