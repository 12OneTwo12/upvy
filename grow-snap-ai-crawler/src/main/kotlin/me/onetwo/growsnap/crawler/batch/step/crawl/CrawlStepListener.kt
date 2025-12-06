package me.onetwo.growsnap.crawler.batch.step.crawl

import org.slf4j.LoggerFactory
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.StepExecutionListener
import org.springframework.stereotype.Component

/**
 * CrawlStep 실행 리스너
 *
 * Step 시작/종료 시 로깅 및 통계 수집을 담당합니다.
 */
@Component
class CrawlStepListener : StepExecutionListener {

    companion object {
        private val logger = LoggerFactory.getLogger(CrawlStepListener::class.java)
    }

    override fun beforeStep(stepExecution: StepExecution) {
        logger.info("=== CrawlStep 시작 ===")
        logger.info("Job ID: {}", stepExecution.jobExecution.jobId)
        logger.info("Step Name: {}", stepExecution.stepName)
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus {
        logger.info("=== CrawlStep 완료 ===")
        logger.info("Read Count: {}", stepExecution.readCount)
        logger.info("Write Count: {}", stepExecution.writeCount)
        logger.info("Skip Count: {}", stepExecution.skipCount)
        logger.info("Status: {}", stepExecution.status)
        logger.info("Exit Status: {}", stepExecution.exitStatus)

        if (stepExecution.failureExceptions.isNotEmpty()) {
            logger.warn("Failures: {}", stepExecution.failureExceptions.size)
            stepExecution.failureExceptions.forEach { e ->
                logger.warn("  - {}: {}", e.javaClass.simpleName, e.message)
            }
        }

        return stepExecution.exitStatus
    }
}
