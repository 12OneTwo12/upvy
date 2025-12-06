package me.onetwo.growsnap.crawler.backoffice.repository

import me.onetwo.growsnap.crawler.backoffice.domain.PendingContent
import me.onetwo.growsnap.crawler.backoffice.domain.PendingContentStatus
import me.onetwo.growsnap.crawler.domain.ReviewPriority
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PendingContentRepository : JpaRepository<PendingContent, Long> {

    /**
     * 상태별 콘텐츠 조회 (페이징, 우선순위/품질점수/생성일 정렬)
     */
    @Query("""
        SELECT p FROM PendingContent p
        WHERE p.status = :status AND p.deletedAt IS NULL
        ORDER BY
            CASE p.reviewPriority
                WHEN 'HIGH' THEN 1
                WHEN 'NORMAL' THEN 2
                WHEN 'LOW' THEN 3
            END,
            p.qualityScore DESC,
            p.createdAt DESC
    """)
    fun findByStatusOrderByPriority(
        @Param("status") status: PendingContentStatus,
        pageable: Pageable
    ): Page<PendingContent>

    /**
     * 승인 대기 콘텐츠 조회 (우선순위 정렬)
     */
    fun findByStatusAndDeletedAtIsNullOrderByReviewPriorityAscQualityScoreDescCreatedAtDesc(
        status: PendingContentStatus,
        pageable: Pageable
    ): Page<PendingContent>

    /**
     * 상태별 개수 조회
     */
    fun countByStatusAndDeletedAtIsNull(status: PendingContentStatus): Long

    /**
     * 우선순위별 개수 조회
     */
    fun countByReviewPriorityAndStatusAndDeletedAtIsNull(
        priority: ReviewPriority,
        status: PendingContentStatus
    ): Long

    /**
     * 오늘 생성된 콘텐츠 개수
     */
    @Query("""
        SELECT COUNT(p) FROM PendingContent p
        WHERE p.createdAt >= :startOfDay AND p.deletedAt IS NULL
    """)
    fun countCreatedToday(@Param("startOfDay") startOfDay: java.time.Instant): Long

    /**
     * 특정 기간 내 승인/거절된 콘텐츠 개수
     */
    @Query("""
        SELECT COUNT(p) FROM PendingContent p
        WHERE p.status = :status
        AND p.reviewedAt >= :since
        AND p.deletedAt IS NULL
    """)
    fun countReviewedSince(
        @Param("status") status: PendingContentStatus,
        @Param("since") since: java.time.Instant
    ): Long

    /**
     * 평균 품질 점수
     */
    @Query("SELECT AVG(p.qualityScore) FROM PendingContent p WHERE p.deletedAt IS NULL")
    fun getAverageQualityScore(): Double?

    /**
     * AI 콘텐츠 Job ID로 조회
     */
    fun findByAiContentJobId(aiContentJobId: Long): PendingContent?

    /**
     * 카테고리별 통계
     */
    @Query("""
        SELECT p.category, COUNT(p)
        FROM PendingContent p
        WHERE p.deletedAt IS NULL
        GROUP BY p.category
    """)
    fun countByCategory(): List<Array<Any>>
}
