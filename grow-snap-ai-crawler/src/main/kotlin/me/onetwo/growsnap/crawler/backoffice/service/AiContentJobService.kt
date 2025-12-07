package me.onetwo.growsnap.crawler.backoffice.service

import me.onetwo.growsnap.crawler.batch.step.analyze.AnalyzeProcessor
import me.onetwo.growsnap.crawler.batch.step.edit.EditProcessor
import me.onetwo.growsnap.crawler.batch.step.review.ReviewProcessor
import me.onetwo.growsnap.crawler.batch.step.transcribe.TranscribeProcessor
import me.onetwo.growsnap.crawler.domain.AiContentJob
import me.onetwo.growsnap.crawler.domain.AiContentJobRepository
import me.onetwo.growsnap.crawler.domain.JobStatus
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * AI Content Job 관리 서비스
 */
@Service
class AiContentJobService(
    private val aiContentJobRepository: AiContentJobRepository,
    private val transcribeProcessor: TranscribeProcessor,
    private val analyzeProcessor: AnalyzeProcessor,
    private val editProcessor: EditProcessor,
    private val reviewProcessor: ReviewProcessor
) {
    companion object {
        private val logger = LoggerFactory.getLogger(AiContentJobService::class.java)
    }

    /**
     * 전체 Job 목록 조회 (페이징)
     */
    fun findAll(page: Int, size: Int): Page<AiContentJob> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return aiContentJobRepository.findAll(pageable)
    }

    /**
     * 상태별 Job 목록 조회
     */
    fun findByStatus(status: JobStatus): List<AiContentJob> {
        return aiContentJobRepository.findByStatus(status)
    }

    /**
     * Job 상세 조회
     */
    fun findById(id: Long): AiContentJob? {
        return aiContentJobRepository.findById(id).orElse(null)
    }

    /**
     * Job 상태 변경 (재처리용)
     */
    @Transactional
    fun updateStatus(id: Long, targetStatus: JobStatus, updatedBy: String): AiContentJob? {
        val job = aiContentJobRepository.findById(id).orElse(null) ?: return null

        val updatedJob = job.copy(
            status = targetStatus,
            updatedAt = Instant.now(),
            updatedBy = updatedBy,
            errorMessage = null  // 에러 메시지 초기화
        )

        val saved = aiContentJobRepository.save(updatedJob)
        logger.info("Job 상태 변경: jobId={}, from={} to={}, by={}",
            id, job.status, targetStatus, updatedBy)

        return saved
    }

    /**
     * Job 재처리 - 특정 단계부터 다시 시작
     */
    @Transactional
    fun retryFrom(id: Long, fromStatus: JobStatus, updatedBy: String): AiContentJob? {
        val job = aiContentJobRepository.findById(id).orElse(null) ?: return null

        // 재처리 가능한 상태인지 확인
        val retryableStatuses = setOf(
            JobStatus.FAILED,
            JobStatus.REJECTED,
            JobStatus.CRAWLED,
            JobStatus.TRANSCRIBED,
            JobStatus.ANALYZED,
            JobStatus.EDITED
        )

        if (job.status !in retryableStatuses && job.status != JobStatus.PENDING_APPROVAL) {
            logger.warn("재처리 불가능한 상태: jobId={}, status={}", id, job.status)
            return null
        }

        val updatedJob = job.copy(
            status = fromStatus,
            updatedAt = Instant.now(),
            updatedBy = updatedBy,
            errorMessage = null
        )

        val saved = aiContentJobRepository.save(updatedJob)
        logger.info("Job 재처리 설정: jobId={}, retryFrom={}, by={}",
            id, fromStatus, updatedBy)

        return saved
    }

    /**
     * Job 상태별 통계
     */
    fun getStatusStats(): Map<JobStatus, Long> {
        return JobStatus.entries.associateWith { status ->
            aiContentJobRepository.findByStatus(status).size.toLong()
        }
    }

    // ========== 직접 실행 메서드 ==========

    /**
     * Transcribe 단계 직접 실행
     * CRAWLED 상태의 Job만 실행 가능
     */
    @Transactional
    fun executeTranscribe(id: Long, executedBy: String): AiContentJob? {
        val job = aiContentJobRepository.findById(id).orElse(null) ?: return null

        if (job.status != JobStatus.CRAWLED) {
            logger.warn("Transcribe 실행 불가: jobId={}, 현재상태={}", id, job.status)
            return null
        }

        logger.info("Transcribe 직접 실행 시작: jobId={}, by={}", id, executedBy)

        return try {
            val result = transcribeProcessor.process(job)
            if (result != null) {
                val saved = aiContentJobRepository.save(result.copy(updatedBy = executedBy))
                logger.info("Transcribe 완료: jobId={}", id)
                saved
            } else {
                val failed = job.copy(
                    status = JobStatus.FAILED,
                    errorMessage = "Transcribe 처리 실패",
                    updatedAt = Instant.now(),
                    updatedBy = executedBy
                )
                aiContentJobRepository.save(failed)
            }
        } catch (e: Exception) {
            logger.error("Transcribe 실행 오류: jobId={}, error={}", id, e.message, e)
            val failed = job.copy(
                status = JobStatus.FAILED,
                errorMessage = "Transcribe 오류: ${e.message}",
                updatedAt = Instant.now(),
                updatedBy = executedBy
            )
            aiContentJobRepository.save(failed)
        }
    }

    /**
     * Analyze 단계 직접 실행
     * TRANSCRIBED 상태의 Job만 실행 가능
     */
    @Transactional
    fun executeAnalyze(id: Long, executedBy: String): AiContentJob? {
        val job = aiContentJobRepository.findById(id).orElse(null) ?: return null

        if (job.status != JobStatus.TRANSCRIBED) {
            logger.warn("Analyze 실행 불가: jobId={}, 현재상태={}", id, job.status)
            return null
        }

        logger.info("Analyze 직접 실행 시작: jobId={}, by={}", id, executedBy)

        return try {
            val result = analyzeProcessor.process(job)
            if (result != null) {
                val saved = aiContentJobRepository.save(result.copy(updatedBy = executedBy))
                logger.info("Analyze 완료: jobId={}", id)
                saved
            } else {
                val failed = job.copy(
                    status = JobStatus.FAILED,
                    errorMessage = "Analyze 처리 실패",
                    updatedAt = Instant.now(),
                    updatedBy = executedBy
                )
                aiContentJobRepository.save(failed)
            }
        } catch (e: Exception) {
            logger.error("Analyze 실행 오류: jobId={}, error={}", id, e.message, e)
            val failed = job.copy(
                status = JobStatus.FAILED,
                errorMessage = "Analyze 오류: ${e.message}",
                updatedAt = Instant.now(),
                updatedBy = executedBy
            )
            aiContentJobRepository.save(failed)
        }
    }

    /**
     * Edit 단계 직접 실행
     * ANALYZED 상태의 Job만 실행 가능
     */
    @Transactional
    fun executeEdit(id: Long, executedBy: String): AiContentJob? {
        val job = aiContentJobRepository.findById(id).orElse(null) ?: return null

        if (job.status != JobStatus.ANALYZED) {
            logger.warn("Edit 실행 불가: jobId={}, 현재상태={}", id, job.status)
            return null
        }

        logger.info("Edit 직접 실행 시작: jobId={}, by={}", id, executedBy)

        return try {
            val result = editProcessor.process(job)
            if (result != null) {
                val saved = aiContentJobRepository.save(result.copy(updatedBy = executedBy))
                logger.info("Edit 완료: jobId={}", id)
                saved
            } else {
                val failed = job.copy(
                    status = JobStatus.FAILED,
                    errorMessage = "Edit 처리 실패",
                    updatedAt = Instant.now(),
                    updatedBy = executedBy
                )
                aiContentJobRepository.save(failed)
            }
        } catch (e: Exception) {
            logger.error("Edit 실행 오류: jobId={}, error={}", id, e.message, e)
            val failed = job.copy(
                status = JobStatus.FAILED,
                errorMessage = "Edit 오류: ${e.message}",
                updatedAt = Instant.now(),
                updatedBy = executedBy
            )
            aiContentJobRepository.save(failed)
        }
    }

    /**
     * Review 단계 직접 실행
     * EDITED 상태의 Job만 실행 가능
     */
    @Transactional
    fun executeReview(id: Long, executedBy: String): AiContentJob? {
        val job = aiContentJobRepository.findById(id).orElse(null) ?: return null

        if (job.status != JobStatus.EDITED) {
            logger.warn("Review 실행 불가: jobId={}, 현재상태={}", id, job.status)
            return null
        }

        logger.info("Review 직접 실행 시작: jobId={}, by={}", id, executedBy)

        return try {
            val result = reviewProcessor.process(job)
            if (result != null) {
                val saved = aiContentJobRepository.save(result.copy(updatedBy = executedBy))
                logger.info("Review 완료: jobId={}", id)
                saved
            } else {
                val failed = job.copy(
                    status = JobStatus.FAILED,
                    errorMessage = "Review 처리 실패",
                    updatedAt = Instant.now(),
                    updatedBy = executedBy
                )
                aiContentJobRepository.save(failed)
            }
        } catch (e: Exception) {
            logger.error("Review 실행 오류: jobId={}, error={}", id, e.message, e)
            val failed = job.copy(
                status = JobStatus.FAILED,
                errorMessage = "Review 오류: ${e.message}",
                updatedAt = Instant.now(),
                updatedBy = executedBy
            )
            aiContentJobRepository.save(failed)
        }
    }
}
