package me.onetwo.upvy.crawler.client.stt

import com.google.cloud.speech.v1.RecognitionAudio
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding
import com.google.cloud.speech.v1.SpeechClient
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.onetwo.upvy.crawler.client.SttClient
import me.onetwo.upvy.crawler.domain.ContentLanguage
import me.onetwo.upvy.crawler.domain.TranscriptResult
import me.onetwo.upvy.crawler.domain.TranscriptSegment
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URL
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

/**
 * Google Cloud Speech-to-Text 클라이언트 구현체
 *
 * Google Cloud Speech-to-Text API를 사용하여 음성을 텍스트로 변환합니다.
 * 긴 오디오 파일의 경우 LongRunningRecognize를 사용합니다.
 */
@Component
@ConditionalOnProperty(name = ["ai.stt.provider"], havingValue = "vertex-ai")
class VertexAiSttClient(
    @Value("\${ai.stt.language-code:ko-KR}") private val languageCode: String,
    @Value("\${ai.stt.sample-rate-hertz:16000}") private val sampleRateHertz: Int,
    @Value("\${ai.stt.enable-word-time-offsets:true}") private val enableWordTimeOffsets: Boolean
) : SttClient {

    companion object {
        private val logger = LoggerFactory.getLogger(VertexAiSttClient::class.java)
        private const val MAX_SYNC_AUDIO_DURATION_SECONDS = 60

        /**
         * ContentLanguage -> Google STT languageCode 매핑
         */
        private fun toSttLanguageCode(language: ContentLanguage): String {
            return when (language) {
                ContentLanguage.KO -> "ko-KR"
                ContentLanguage.EN -> "en-US"
                ContentLanguage.JA -> "ja-JP"
            }
        }
    }

    private lateinit var speechClient: SpeechClient

    @PostConstruct
    fun init() {
        logger.info("Google Cloud Speech-to-Text 클라이언트 초기화: languageCode={}", languageCode)
        speechClient = SpeechClient.create()
    }

    @PreDestroy
    fun destroy() {
        speechClient.close()
    }

    override suspend fun transcribe(audioUrl: String, language: ContentLanguage): TranscriptResult = withContext(Dispatchers.IO) {
        val sttLanguageCode = toSttLanguageCode(language)
        logger.info("음성 텍스트 변환 시작: audioUrl={}, language={}", audioUrl, sttLanguageCode)

        try {
            val recognitionConfig = buildRecognitionConfig(sttLanguageCode)

            // GCS URI인 경우 LongRunningRecognize 사용 (60초 이상 오디오 지원)
            if (audioUrl.startsWith("gs://")) {
                return@withContext transcribeLongAudio(audioUrl, language)
            }

            // 오디오 크기로 60초 이상 여부 추정 (OGG_OPUS: 약 12KB/초)
            // 60초 * 12KB = 720KB = 737,280 bytes
            val audioBytes = URI(audioUrl).toURL().openStream().use { it.readBytes() }
            val estimatedDurationSeconds = audioBytes.size / 12_000

            if (estimatedDurationSeconds > MAX_SYNC_AUDIO_DURATION_SECONDS) {
                logger.info("오디오 길이 추정 {}초 (>60초), GCS 업로드 후 LongRunningRecognize 사용 권장", estimatedDurationSeconds)
                // 60초 이상인 경우 동기 API 제한으로 인해 경고 로그
                // 실제 운영에서는 GCS에 업로드 후 transcribeLongAudio 호출 필요
            }

            // 동기 API 호출 (60초 미만 오디오용)
            val response = speechClient.recognize(recognitionConfig, RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(audioBytes))
                .build())

            val segments = mutableListOf<TranscriptSegment>()
            val textBuilder = StringBuilder()

            response.resultsList.forEach { result ->
                val alternative = result.alternativesList.firstOrNull() ?: return@forEach

                textBuilder.append(alternative.transcript).append(" ")

                // 단어 레벨 타임스탬프가 있는 경우
                if (enableWordTimeOffsets && alternative.wordsCount > 0) {
                    val words = alternative.wordsList
                    var segmentStart = words.first().startTime.seconds * 1000 +
                            words.first().startTime.nanos / 1_000_000
                    var segmentEnd = segmentStart
                    val segmentText = StringBuilder()

                    words.forEach { wordInfo ->
                        val wordStartMs = wordInfo.startTime.seconds * 1000 +
                                wordInfo.startTime.nanos / 1_000_000
                        val wordEndMs = wordInfo.endTime.seconds * 1000 +
                                wordInfo.endTime.nanos / 1_000_000

                        // 세그먼트 구분 (5초 간격 또는 문장 끝)
                        if (wordStartMs - segmentEnd > 1000 || segmentText.length > 200) {
                            if (segmentText.isNotEmpty()) {
                                segments.add(TranscriptSegment(
                                    startTimeMs = segmentStart,
                                    endTimeMs = segmentEnd,
                                    text = segmentText.toString().trim()
                                ))
                            }
                            segmentStart = wordStartMs
                            segmentText.clear()
                        }

                        segmentText.append(wordInfo.word).append(" ")
                        segmentEnd = wordEndMs
                    }

                    // 마지막 세그먼트 추가
                    if (segmentText.isNotEmpty()) {
                        segments.add(TranscriptSegment(
                            startTimeMs = segmentStart,
                            endTimeMs = segmentEnd,
                            text = segmentText.toString().trim()
                        ))
                    }
                }
            }

            val fullText = textBuilder.toString().trim()
            val confidence = response.resultsList.firstOrNull()?.alternativesList?.firstOrNull()?.confidence

            logger.info("음성 텍스트 변환 완료: textLength={}, segmentsCount={}, confidence={}",
                fullText.length, segments.size, confidence)

            TranscriptResult(
                text = fullText,
                segments = segments,
                language = sttLanguageCode,
                confidence = confidence
            )

        } catch (e: Exception) {
            logger.error("음성 텍스트 변환 실패: audioUrl={}", audioUrl, e)
            throw SttException("Failed to transcribe audio", e)
        }
    }

    /**
     * 긴 오디오 파일 (60초 이상)에 대한 비동기 처리
     */
    suspend fun transcribeLongAudio(gcsUri: String, language: ContentLanguage = ContentLanguage.KO): TranscriptResult = withContext(Dispatchers.IO) {
        val sttLanguageCode = toSttLanguageCode(language)
        logger.info("긴 오디오 텍스트 변환 시작 (LongRunningRecognize): gcsUri={}, language={}", gcsUri, sttLanguageCode)

        try {
            val recognitionConfig = buildRecognitionConfig(sttLanguageCode)
            val audio = RecognitionAudio.newBuilder()
                .setUri(gcsUri)
                .build()

            // 비동기 처리 시작
            val response = speechClient.longRunningRecognizeAsync(recognitionConfig, audio).get()

            val segments = mutableListOf<TranscriptSegment>()
            val textBuilder = StringBuilder()

            response.resultsList.forEach { result ->
                val alternative = result.alternativesList.firstOrNull() ?: return@forEach
                textBuilder.append(alternative.transcript).append(" ")

                // 단어 레벨 타임스탬프가 있는 경우 (transcribe와 동일한 로직)
                if (enableWordTimeOffsets && alternative.wordsCount > 0) {
                    val words = alternative.wordsList
                    var segmentStart = words.first().startTime.seconds * 1000 +
                            words.first().startTime.nanos / 1_000_000
                    var segmentEnd = segmentStart
                    val segmentText = StringBuilder()

                    words.forEach { wordInfo ->
                        val wordStartMs = wordInfo.startTime.seconds * 1000 +
                                wordInfo.startTime.nanos / 1_000_000
                        val wordEndMs = wordInfo.endTime.seconds * 1000 +
                                wordInfo.endTime.nanos / 1_000_000

                        // 세그먼트 구분 (1초 이상 간격 또는 200자 이상)
                        if (wordStartMs - segmentEnd > 1000 || segmentText.length > 200) {
                            if (segmentText.isNotEmpty()) {
                                segments.add(TranscriptSegment(
                                    startTimeMs = segmentStart,
                                    endTimeMs = segmentEnd,
                                    text = segmentText.toString().trim()
                                ))
                            }
                            segmentStart = wordStartMs
                            segmentText.clear()
                        }

                        segmentText.append(wordInfo.word).append(" ")
                        segmentEnd = wordEndMs
                    }

                    // 마지막 세그먼트 추가
                    if (segmentText.isNotEmpty()) {
                        segments.add(TranscriptSegment(
                            startTimeMs = segmentStart,
                            endTimeMs = segmentEnd,
                            text = segmentText.toString().trim()
                        ))
                    }
                } else if (result.resultEndTime != null) {
                    // 단어 레벨 타임스탬프가 없는 경우 결과 레벨에서 추출 (fallback)
                    val endMs = result.resultEndTime.seconds * 1000 +
                            result.resultEndTime.nanos / 1_000_000

                    segments.add(TranscriptSegment(
                        startTimeMs = segments.lastOrNull()?.endTimeMs ?: 0,
                        endTimeMs = endMs,
                        text = alternative.transcript
                    ))
                }
            }

            val fullText = textBuilder.toString().trim()
            val confidence = response.resultsList.firstOrNull()?.alternativesList?.firstOrNull()?.confidence

            logger.info("긴 오디오 텍스트 변환 완료: textLength={}, segmentsCount={}, confidence={}",
                fullText.length, segments.size, confidence)

            TranscriptResult(
                text = fullText,
                segments = segments,
                language = sttLanguageCode,
                confidence = confidence
            )

        } catch (e: Exception) {
            logger.error("긴 오디오 텍스트 변환 실패: gcsUri={}", gcsUri, e)
            throw SttException("Failed to transcribe long audio", e)
        }
    }

    private fun buildRecognitionConfig(sttLanguageCode: String): RecognitionConfig {
        return RecognitionConfig.newBuilder()
            .setEncoding(AudioEncoding.OGG_OPUS)  // Opus 코덱 (음성에 최적화, 작은 파일)
            .setSampleRateHertz(sampleRateHertz)
            .setLanguageCode(sttLanguageCode)
            .setEnableWordTimeOffsets(enableWordTimeOffsets)
            .setEnableAutomaticPunctuation(true)
            .setModel("latest_long")  // 긴 오디오에 최적화된 모델
            .build()
    }
}

/**
 * STT 예외
 */
class SttException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
