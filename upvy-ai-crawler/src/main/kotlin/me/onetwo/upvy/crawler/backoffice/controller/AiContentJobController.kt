package me.onetwo.upvy.crawler.backoffice.controller

import me.onetwo.upvy.crawler.backoffice.service.AiContentJobService
import me.onetwo.upvy.crawler.domain.JobStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.security.Principal

/**
 * AI Content Job 관리 컨트롤러
 */
@Controller
@RequestMapping("/backoffice/ai-jobs")
class AiContentJobController(
    private val aiContentJobService: AiContentJobService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(AiContentJobController::class.java)
    }

    /**
     * Job 목록 조회
     */
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: JobStatus?,
        model: Model
    ): String {
        val jobs = if (status != null) {
            aiContentJobService.findByStatus(status)
        } else {
            aiContentJobService.findAll(page, size).content
        }

        val statusStats = aiContentJobService.getStatusStats()

        model.addAttribute("jobs", jobs)
        model.addAttribute("statusStats", statusStats)
        model.addAttribute("selectedStatus", status)
        model.addAttribute("allStatuses", JobStatus.entries)
        model.addAttribute("currentPage", page)

        return "backoffice/ai-jobs/list"
    }

    /**
     * Job 상세 조회
     */
    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long, model: Model): String {
        val job = aiContentJobService.findById(id)
            ?: return "redirect:/backoffice/ai-jobs?error=notfound"

        model.addAttribute("job", job)
        model.addAttribute("allStatuses", JobStatus.entries)

        return "backoffice/ai-jobs/detail"
    }

    /**
     * Job 재처리 - 특정 단계부터 다시 시작
     */
    @PostMapping("/{id}/retry")
    fun retry(
        @PathVariable id: Long,
        @RequestParam fromStatus: JobStatus,
        principal: Principal,
        redirectAttributes: RedirectAttributes
    ): String {
        val result = aiContentJobService.retryFrom(id, fromStatus, principal.name)

        if (result != null) {
            logger.info("Job 재처리 설정 완료: jobId={}, fromStatus={}, by={}",
                id, fromStatus, principal.name)
            redirectAttributes.addFlashAttribute("successMessage",
                "Job #$id 를 ${fromStatus.name} 단계부터 재처리하도록 설정했습니다. Job 실행 시 처리됩니다.")
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Job 재처리 설정 실패. 재처리 가능한 상태인지 확인하세요.")
        }

        return "redirect:/backoffice/ai-jobs/$id"
    }

    /**
     * Job 상태 직접 변경
     */
    @PostMapping("/{id}/status")
    fun updateStatus(
        @PathVariable id: Long,
        @RequestParam targetStatus: JobStatus,
        principal: Principal,
        redirectAttributes: RedirectAttributes
    ): String {
        val result = aiContentJobService.updateStatus(id, targetStatus, principal.name)

        if (result != null) {
            redirectAttributes.addFlashAttribute("successMessage",
                "Job #$id 상태가 ${targetStatus.name}(으)로 변경되었습니다.")
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "상태 변경 실패")
        }

        return "redirect:/backoffice/ai-jobs/$id"
    }

    /**
     * 여러 Job 일괄 재처리
     */
    @PostMapping("/batch-retry")
    fun batchRetry(
        @RequestParam jobIds: List<Long>,
        @RequestParam fromStatus: JobStatus,
        principal: Principal,
        redirectAttributes: RedirectAttributes
    ): String {
        var successCount = 0
        var failCount = 0

        jobIds.forEach { id ->
            val result = aiContentJobService.retryFrom(id, fromStatus, principal.name)
            if (result != null) successCount++ else failCount++
        }

        redirectAttributes.addFlashAttribute("successMessage",
            "일괄 재처리 설정 완료: 성공 $successCount, 실패 $failCount")

        return "redirect:/backoffice/ai-jobs"
    }

    // ========== 직접 실행 엔드포인트 ==========

    /**
     * Transcribe 단계 직접 실행
     */
    @PostMapping("/{id}/execute/transcribe")
    fun executeTranscribe(
        @PathVariable id: Long,
        principal: Principal,
        redirectAttributes: RedirectAttributes
    ): String {
        logger.info("Transcribe 직접 실행 요청: jobId={}, by={}", id, principal.name)

        val result = aiContentJobService.executeTranscribe(id, principal.name)

        if (result != null && result.status == JobStatus.TRANSCRIBED) {
            redirectAttributes.addFlashAttribute("successMessage",
                "Transcribe 완료! 상태: ${result.status.name}")
        } else if (result != null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Transcribe 실패: ${result.errorMessage ?: "알 수 없는 오류"}")
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Transcribe 실행 불가. 현재 상태가 CRAWLED인지 확인하세요.")
        }

        return "redirect:/backoffice/ai-jobs/$id"
    }

    /**
     * Analyze 단계 직접 실행
     */
    @PostMapping("/{id}/execute/analyze")
    fun executeAnalyze(
        @PathVariable id: Long,
        principal: Principal,
        redirectAttributes: RedirectAttributes
    ): String {
        logger.info("Analyze 직접 실행 요청: jobId={}, by={}", id, principal.name)

        val result = aiContentJobService.executeAnalyze(id, principal.name)

        if (result != null && result.status == JobStatus.ANALYZED) {
            redirectAttributes.addFlashAttribute("successMessage",
                "Analyze 완료! 상태: ${result.status.name}")
        } else if (result != null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Analyze 실패: ${result.errorMessage ?: "알 수 없는 오류"}")
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Analyze 실행 불가. 현재 상태가 TRANSCRIBED인지 확인하세요.")
        }

        return "redirect:/backoffice/ai-jobs/$id"
    }

    /**
     * Edit 단계 직접 실행
     */
    @PostMapping("/{id}/execute/edit")
    fun executeEdit(
        @PathVariable id: Long,
        principal: Principal,
        redirectAttributes: RedirectAttributes
    ): String {
        logger.info("Edit 직접 실행 요청: jobId={}, by={}", id, principal.name)

        val result = aiContentJobService.executeEdit(id, principal.name)

        if (result != null && result.status == JobStatus.EDITED) {
            redirectAttributes.addFlashAttribute("successMessage",
                "Edit 완료! 상태: ${result.status.name}")
        } else if (result != null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Edit 실패: ${result.errorMessage ?: "알 수 없는 오류"}")
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Edit 실행 불가. 현재 상태가 ANALYZED인지 확인하세요.")
        }

        return "redirect:/backoffice/ai-jobs/$id"
    }

    /**
     * Review 단계 직접 실행
     */
    @PostMapping("/{id}/execute/review")
    fun executeReview(
        @PathVariable id: Long,
        principal: Principal,
        redirectAttributes: RedirectAttributes
    ): String {
        logger.info("Review 직접 실행 요청: jobId={}, by={}", id, principal.name)

        val result = aiContentJobService.executeReview(id, principal.name)

        if (result != null && (result.status == JobStatus.PENDING_APPROVAL || result.status == JobStatus.REJECTED)) {
            redirectAttributes.addFlashAttribute("successMessage",
                "Review 완료! 상태: ${result.status.name}, 점수: ${result.qualityScore}")
        } else if (result != null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Review 실패: ${result.errorMessage ?: "알 수 없는 오류"}")
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Review 실행 불가. 현재 상태가 EDITED인지 확인하세요.")
        }

        return "redirect:/backoffice/ai-jobs/$id"
    }
}
