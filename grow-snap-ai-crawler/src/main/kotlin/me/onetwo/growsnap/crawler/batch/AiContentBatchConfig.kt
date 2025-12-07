package me.onetwo.growsnap.crawler.batch

import me.onetwo.growsnap.crawler.batch.step.analyze.AnalyzeProcessor
import me.onetwo.growsnap.crawler.batch.step.analyze.AnalyzeReader
import me.onetwo.growsnap.crawler.batch.step.analyze.AnalyzeWriter
import me.onetwo.growsnap.crawler.batch.step.crawl.AiContentJobWriter
import me.onetwo.growsnap.crawler.batch.step.crawl.AiPoweredSearchReader
import me.onetwo.growsnap.crawler.batch.step.crawl.CrawlStepListener
import me.onetwo.growsnap.crawler.batch.step.crawl.VideoDownloadProcessor
import me.onetwo.growsnap.crawler.batch.step.edit.EditProcessor
import me.onetwo.growsnap.crawler.batch.step.edit.EditReader
import me.onetwo.growsnap.crawler.batch.step.edit.EditWriter
import me.onetwo.growsnap.crawler.batch.step.review.ReviewProcessor
import me.onetwo.growsnap.crawler.batch.step.review.ReviewReader
import me.onetwo.growsnap.crawler.batch.step.review.ReviewWriter
import me.onetwo.growsnap.crawler.batch.step.transcribe.TranscribeProcessor
import me.onetwo.growsnap.crawler.batch.step.transcribe.TranscribeReader
import me.onetwo.growsnap.crawler.batch.step.transcribe.TranscribeWriter
import me.onetwo.growsnap.crawler.client.video.YtDlpException
import me.onetwo.growsnap.crawler.client.youtube.YouTubeApiException
import me.onetwo.growsnap.crawler.domain.AiContentJob
import me.onetwo.growsnap.crawler.domain.EvaluatedVideo
import me.onetwo.growsnap.crawler.service.AudioExtractException
import me.onetwo.growsnap.crawler.service.S3Exception
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.io.IOException

/**
 * AI 콘텐츠 배치 Job 설정
 *
 * 5단계 파이프라인:
 * 1. CrawlStep - AI 기반 YouTube 검색 및 다운로드
 * 2. TranscribeStep - 음성-텍스트 변환
 * 3. AnalyzeStep - LLM 분석 및 메타데이터 생성
 * 4. EditStep - 비디오 편집 (클리핑, 리사이징)
 * 5. ReviewStep - 품질 검토 및 승인 대기
 */
@Configuration
class AiContentBatchConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager
) {

    companion object {
        private val logger = LoggerFactory.getLogger(AiContentBatchConfig::class.java)
    }

    @Value("\${batch.chunk-size:5}")
    private var chunkSize: Int = 5

    @Value("\${batch.retry.max-attempts:3}")
    private var maxRetryAttempts: Int = 3

    /**
     * AI 콘텐츠 생성 Job
     */
    @Bean
    fun aiContentJob(
        crawlStep: Step,
        transcribeStep: Step,
        analyzeStep: Step,
        editStep: Step,
        reviewStep: Step
    ): Job {
        logger.info("AI Content Job 설정: chunkSize={}, maxRetry={}", chunkSize, maxRetryAttempts)

        return JobBuilder("aiContentJob", jobRepository)
            .start(crawlStep)
            .next(transcribeStep)
            .next(analyzeStep)
            .next(editStep)
            .next(reviewStep)
            .build()
    }

    /**
     * Step 1: CrawlStep - AI 기반 YouTube 검색 및 다운로드
     */
    @Bean
    fun crawlStep(
        aiPoweredSearchReader: AiPoweredSearchReader,
        videoDownloadProcessor: VideoDownloadProcessor,
        aiContentJobWriter: AiContentJobWriter,
        crawlStepListener: CrawlStepListener
    ): Step {
        return StepBuilder("crawlStep", jobRepository)
            .chunk<EvaluatedVideo, AiContentJob>(chunkSize, transactionManager)
            .reader(aiPoweredSearchReader)
            .processor(videoDownloadProcessor)
            .writer(aiContentJobWriter)
            .faultTolerant()
            .retry(IOException::class.java)
            .retry(YtDlpException::class.java)
            .retry(S3Exception::class.java)
            .retryLimit(maxRetryAttempts)
            .skip(YouTubeApiException::class.java)
            .skip(YtDlpException::class.java)
            .skipLimit(100)
            .listener(crawlStepListener)
            .build()
    }

    /**
     * Step 2: TranscribeStep - 음성-텍스트 변환
     */
    @Bean
    fun transcribeStep(
        transcribeReader: TranscribeReader,
        transcribeProcessor: TranscribeProcessor,
        transcribeWriter: TranscribeWriter
    ): Step {
        return StepBuilder("transcribeStep", jobRepository)
            .chunk<AiContentJob, AiContentJob>(chunkSize, transactionManager)
            .reader(transcribeReader)
            .processor(transcribeProcessor)
            .writer(transcribeWriter)
            .faultTolerant()
            .retry(IOException::class.java)
            .retry(AudioExtractException::class.java)
            .retryLimit(maxRetryAttempts)
            .skip(AudioExtractException::class.java)
            .skipLimit(50)
            .build()
    }

    /**
     * Step 3: AnalyzeStep - LLM 분석 및 메타데이터 생성
     */
    @Bean
    fun analyzeStep(
        analyzeReader: AnalyzeReader,
        analyzeProcessor: AnalyzeProcessor,
        analyzeWriter: AnalyzeWriter
    ): Step {
        return StepBuilder("analyzeStep", jobRepository)
            .chunk<AiContentJob, AiContentJob>(chunkSize, transactionManager)
            .reader(analyzeReader)
            .processor(analyzeProcessor)
            .writer(analyzeWriter)
            .faultTolerant()
            .retry(IOException::class.java)
            .retryLimit(maxRetryAttempts)
            .skipLimit(50)
            .build()
    }

    /**
     * Step 4: EditStep - 비디오 편집 (클리핑, 리사이징)
     */
    @Bean
    fun editStep(
        editReader: EditReader,
        editProcessor: EditProcessor,
        editWriter: EditWriter
    ): Step {
        return StepBuilder("editStep", jobRepository)
            .chunk<AiContentJob, AiContentJob>(chunkSize, transactionManager)
            .reader(editReader)
            .processor(editProcessor)
            .writer(editWriter)
            .faultTolerant()
            .retry(IOException::class.java)
            .retryLimit(maxRetryAttempts)
            .skipLimit(50)
            .build()
    }

    /**
     * Step 5: ReviewStep - 품질 검토 및 승인 대기
     */
    @Bean
    fun reviewStep(
        reviewReader: ReviewReader,
        reviewProcessor: ReviewProcessor,
        reviewWriter: ReviewWriter
    ): Step {
        return StepBuilder("reviewStep", jobRepository)
            .chunk<AiContentJob, AiContentJob>(chunkSize, transactionManager)
            .reader(reviewReader)
            .processor(reviewProcessor)
            .writer(reviewWriter)
            .build()
    }
}
