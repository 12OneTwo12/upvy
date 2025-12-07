package me.onetwo.growsnap.crawler.backoffice.controller

import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.security.Principal
import java.time.Instant

@Controller
@RequestMapping("/backoffice/jobs")
class JobController(
    private val jobLauncher: JobLauncher,
    private val jobExplorer: JobExplorer,
    @Qualifier("aiContentJob")
    private val aiContentJob: Job
) {
    companion object {
        private val logger = LoggerFactory.getLogger(JobController::class.java)
    }

    @GetMapping
    fun jobDashboard(model: Model): String {
        // 최근 Job 실행 내역 조회
        val recentExecutions = jobExplorer.getJobNames()
            .flatMap { jobName ->
                jobExplorer.getJobInstances(jobName, 0, 10)
                    .flatMap { instance ->
                        jobExplorer.getJobExecutions(instance)
                    }
            }
            .sortedByDescending { it.startTime }
            .take(20)

        model.addAttribute("recentExecutions", recentExecutions)
        model.addAttribute("isJobRunning", isAnyJobRunning())

        return "backoffice/jobs/dashboard"
    }

    @PostMapping("/trigger")
    fun triggerJob(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false, defaultValue = "5") maxVideos: Int,
        principal: Principal,
        redirectAttributes: RedirectAttributes
    ): String {
        if (isAnyJobRunning()) {
            redirectAttributes.addFlashAttribute("errorMessage", "이미 실행 중인 Job이 있습니다. 완료 후 다시 시도해주세요.")
            return "redirect:/backoffice/jobs"
        }

        return try {
            val params = JobParametersBuilder()
                .addString("triggeredBy", principal.name)
                .addString("triggeredAt", Instant.now().toString())
                .addString("category", category ?: "ALL")
                .addLong("maxVideos", maxVideos.toLong())
                .addLong("timestamp", System.currentTimeMillis()) // 유니크 파라미터
                .toJobParameters()

            val execution = jobLauncher.run(aiContentJob, params)

            logger.info("Job 수동 실행 시작: executionId={}, triggeredBy={}", execution.id, principal.name)

            redirectAttributes.addFlashAttribute("successMessage",
                "Job이 시작되었습니다. (Execution ID: ${execution.id})")
            "redirect:/backoffice/jobs"
        } catch (e: Exception) {
            logger.error("Job 실행 실패", e)
            redirectAttributes.addFlashAttribute("errorMessage", "Job 실행 실패: ${e.message}")
            "redirect:/backoffice/jobs"
        }
    }

    @GetMapping("/executions/{executionId}")
    fun executionDetail(@PathVariable executionId: Long, model: Model): String {
        val execution = jobExplorer.getJobExecution(executionId)
            ?: return "redirect:/backoffice/jobs?error=notfound"

        model.addAttribute("execution", execution)
        model.addAttribute("stepExecutions", execution.stepExecutions)

        return "backoffice/jobs/execution-detail"
    }

    @ResponseBody
    @GetMapping("/api/status")
    fun getJobStatus(): Map<String, Any> {
        val runningExecutions = jobExplorer.getJobNames()
            .flatMap { jobName ->
                jobExplorer.findRunningJobExecutions(jobName)
            }

        return mapOf(
            "isRunning" to runningExecutions.isNotEmpty(),
            "runningCount" to runningExecutions.size,
            "runningExecutions" to runningExecutions.map { exec ->
                mapOf(
                    "id" to exec.id,
                    "jobName" to exec.jobInstance.jobName,
                    "startTime" to exec.startTime?.toString(),
                    "status" to exec.status.name
                )
            }
        )
    }

    private fun isAnyJobRunning(): Boolean {
        return jobExplorer.getJobNames().any { jobName ->
            jobExplorer.findRunningJobExecutions(jobName).isNotEmpty()
        }
    }
}
