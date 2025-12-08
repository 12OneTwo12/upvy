package me.onetwo.upvy.crawler.scheduler

import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * AI 콘텐츠 배치 스케줄러
 *
 * 설정된 cron 스케줄에 따라 AI 콘텐츠 생성 배치 작업을 실행합니다.
 */
@Component
class AiContentScheduler(
    private val jobLauncher: JobLauncher,
    private val aiContentJob: Job
) {
    companion object {
        private val logger = LoggerFactory.getLogger(AiContentScheduler::class.java)
    }

    @Value("\${batch.schedule.cron}")
    private lateinit var cronExpression: String

    /**
     * AI 콘텐츠 생성 배치 실행
     *
     * 기본: 매일 새벽 3시 실행
     * 설정: batch.schedule.cron 프로퍼티로 변경 가능
     */
    @Scheduled(cron = "\${batch.schedule.cron}")
    fun runDailyBatch() {
        logger.info("AI 콘텐츠 배치 작업 시작")

        try {
            val params = JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters()

            val execution = jobLauncher.run(aiContentJob, params)

            logger.info(
                "배치 완료: status={}, exitStatus={}",
                execution.status,
                execution.exitStatus
            )
        } catch (e: Exception) {
            logger.error("배치 실행 실패", e)
        }
    }

    /**
     * 수동 배치 실행 (테스트/관리자용)
     *
     * @return Job 실행 ID
     */
    fun runManually(): Long {
        logger.info("수동 배치 실행 요청")

        val params = JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .addString("trigger", "manual")
            .toJobParameters()

        val execution = jobLauncher.run(aiContentJob, params)

        logger.info("수동 배치 시작: executionId={}", execution.id)

        return execution.id ?: -1
    }
}
