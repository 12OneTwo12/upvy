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
import me.onetwo.upvy.crawler.service.AudioExtractService
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
 * 긴 오디오 파일의 경우 청크로 분할하여 처리합니다.
 */
@Component
@ConditionalOnProperty(name = ["ai.stt.provider"], havingValue = "vertex-ai")
class VertexAiSttClient(
    @Value("\${ai.stt.language-code:ko-KR}") private val languageCode: String,
    @Value("\${ai.stt.sample-rate-hertz:16000}") private val sampleRateHertz: Int,
    @Value("\${ai.stt.enable-word-time-offsets:true}") private val enableWordTimeOffsets: Boolean,
    private val audioExtractService: AudioExtractService
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

        /**
         * STT 결과 텍스트 정리 (언어별 처리)
         *
         * Google Cloud STT는 한글/일본어에서 "▁" (언더스코어 + 공백) 같은
         * 토큰화 문자를 포함할 수 있습니다. 이를 언어별로 정리합니다.
         *
         * - 일본어: 원래 띄어쓰기가 없으므로 "▁"와 공백 모두 제거
         * - 한글/영어: "▁"를 공백으로 치환하고 불필요한 공백 정리
         */
        private fun cleanSttText(text: String, languageCode: String): String {
            return if (languageCode.startsWith("ja")) {
                // 일본어: 띄어쓰기 없음
                text
                    .replace("▁", "")       // 특수 토큰 제거
                    .replace(" ", "")       // 모든 공백 제거
                    .trim()
            } else {
                // 한글/영어: 띄어쓰기 유지
                text
                    .replace("▁", " ")      // 특수 토큰을 공백으로
                    .replace("  ", " ")     // 이중 공백 제거
                    .replace(" ,", ",")     // 쉼표 앞 공백 제거
                    .replace(" .", ".")     // 마침표 앞 공백 제거
                    .replace(" ?", "?")     // 물음표 앞 공백 제거
                    .replace(" !", "!")     // 느낌표 앞 공백 제거
                    .trim()
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

            // 오디오 길이 확인
            val audioDuration = audioExtractService.getAudioDuration(audioUrl)
            logger.debug("오디오 길이: {}초", audioDuration)

            // 60초 이상인 경우 청크로 분할하여 처리
            if (audioDuration > MAX_SYNC_AUDIO_DURATION_SECONDS) {
                logger.info("오디오 길이 {}초 (>60초), 청크로 분할하여 처리", audioDuration)
                return@withContext transcribeChunks(audioUrl, sttLanguageCode)
            }

            // 동기 API 호출 (60초 미만 오디오용)
            val audioBytes = URI(audioUrl).toURL().openStream().use { it.readBytes() }
            val response = speechClient.recognize(recognitionConfig, RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(audioBytes))
                .build())

            // 디버깅: 응답 상태 로깅
            logger.debug("STT 응답: resultCount={}, fileSize={}bytes",
                response.resultsList.size, audioBytes.size)

            if (response.resultsList.isEmpty()) {
                logger.warn("STT 결과 없음: 오디오에 음성이 없거나 인식 실패")
            }

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
                    val segmentWords = mutableListOf<String>()
                    var wordCount = 0

                    words.forEach { wordInfo ->
                        val wordStartMs = wordInfo.startTime.seconds * 1000 +
                                wordInfo.startTime.nanos / 1_000_000
                        val wordEndMs = wordInfo.endTime.seconds * 1000 +
                                wordInfo.endTime.nanos / 1_000_000

                        // 세그먼트 구분: 2단어마다 또는 0.8초 이상 쉴 때
                        if (wordCount >= 2 || wordStartMs - segmentEnd > 800) {
                            if (segmentWords.isNotEmpty()) {
                                val cleanedText = segmentWords.joinToString(" ")  // 단어 사이 띄어쓰기 유지
                                    .trim()

                                if (cleanedText.isNotEmpty()) {
                                    segments.add(TranscriptSegment(
                                        startTimeMs = segmentStart,
                                        endTimeMs = segmentEnd,
                                        text = cleanedText
                                    ))
                                }
                            }
                            segmentStart = wordStartMs
                            segmentWords.clear()
                            wordCount = 0
                        }

                        // 단어 정리 후 추가 (특수문자만 제거, 띄어쓰기는 유지)
                        val cleanWord = wordInfo.word
                            .replace("▁", "")
                            .replace("_", "")
                            .replace(" ", "")
                            .trim()

                        if (cleanWord.isNotEmpty()) {
                            segmentWords.add(cleanWord)
                            wordCount++
                        }

                        segmentEnd = wordEndMs
                    }

                    // 마지막 세그먼트 추가
                    if (segmentWords.isNotEmpty()) {
                        val cleanedText = segmentWords.joinToString(" ")  // 단어 사이 띄어쓰기 유지
                            .trim()

                        if (cleanedText.isNotEmpty()) {
                            segments.add(TranscriptSegment(
                                startTimeMs = segmentStart,
                                endTimeMs = segmentEnd,
                                text = cleanedText
                            ))
                        }
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
     * 오디오를 청크로 분할하여 처리
     */
    private suspend fun transcribeChunks(audioUrl: String, sttLanguageCode: String): TranscriptResult = withContext(Dispatchers.IO) {
        logger.info("청크 분할 처리 시작: audioUrl={}, language={}", audioUrl, sttLanguageCode)

        try {
            // 오디오를 55초 청크로 분할
            val chunks = audioExtractService.extractAudioChunksFromUrl(audioUrl, chunkDurationSeconds = 55)
            logger.info("청크 분할 완료: 총 {}개 청크", chunks.size)

            val recognitionConfig = buildRecognitionConfig(sttLanguageCode)
            val allSegments = mutableListOf<TranscriptSegment>()
            val textBuilder = StringBuilder()
            var totalConfidence = 0.0
            var confidenceCount = 0

            // 각 청크를 순차적으로 처리
            chunks.forEach { chunk ->
                logger.info("청크 STT 처리 시작: file={}, start={}초", chunk.filePath, chunk.startTimeSeconds)

                val chunkBytes = java.io.File(chunk.filePath).readBytes()
                val response = speechClient.recognize(
                    recognitionConfig,
                    RecognitionAudio.newBuilder()
                        .setContent(ByteString.copyFrom(chunkBytes))
                        .build()
                )

                // 디버깅: 응답 상태 로깅
                logger.debug("STT 응답: resultCount={}, fileSize={}bytes",
                    response.resultsList.size, chunkBytes.size)

                if (response.resultsList.isEmpty()) {
                    logger.warn("STT 결과 없음: chunk={}, 오디오에 음성이 없거나 인식 실패", chunk.filePath)
                }

                // 결과 처리
                response.resultsList.forEach { result ->
                    val alternative = result.alternativesList.firstOrNull() ?: return@forEach
                    textBuilder.append(alternative.transcript).append(" ")

                    if (alternative.confidence > 0) {
                        totalConfidence += alternative.confidence
                        confidenceCount++
                    }

                    // 단어 레벨 타임스탬프 처리 (청크 시작 시간 오프셋 추가)
                    if (enableWordTimeOffsets && alternative.wordsCount > 0) {
                        val words = alternative.wordsList
                        var segmentStart = 0L
                        var segmentEnd = 0L
                        val segmentWords = mutableListOf<String>()
                        var wordCount = 0
                        var isFirstWord = true

                        words.forEach { wordInfo ->
                            val wordStartMs = (wordInfo.startTime.seconds * 1000 +
                                    wordInfo.startTime.nanos / 1_000_000 +
                                    (chunk.startTimeSeconds * 1000).toLong())
                            val wordEndMs = (wordInfo.endTime.seconds * 1000 +
                                    wordInfo.endTime.nanos / 1_000_000 +
                                    (chunk.startTimeSeconds * 1000).toLong())

                            if (isFirstWord) {
                                segmentStart = wordStartMs
                                isFirstWord = false
                            }

                            // 세그먼트 구분: 2단어마다 또는 0.8초 이상 쉴 때
                            if (wordCount >= 2 || wordStartMs - segmentEnd > 800) {
                                if (segmentWords.isNotEmpty()) {
                                    val cleanedText = segmentWords.joinToString(" ")  // 단어 사이 띄어쓰기 유지
                                        .trim()

                                    if (cleanedText.isNotEmpty()) {
                                        allSegments.add(TranscriptSegment(
                                            startTimeMs = segmentStart,
                                            endTimeMs = segmentEnd,
                                            text = cleanedText
                                        ))
                                    }
                                }
                                segmentStart = wordStartMs
                                segmentWords.clear()
                                wordCount = 0
                            }

                            // 단어 정리 후 추가 (특수문자만 제거, 띄어쓰기는 유지)
                            val cleanWord = wordInfo.word
                                .replace("▁", "")
                                .replace("_", "")
                                .replace(" ", "")
                                .trim()

                            if (cleanWord.isNotEmpty()) {
                                segmentWords.add(cleanWord)
                                wordCount++
                            }

                            segmentEnd = wordEndMs
                        }

                        // 마지막 세그먼트 추가
                        if (segmentWords.isNotEmpty()) {
                            val cleanedText = segmentWords.joinToString(" ")  // 단어 사이 띄어쓰기 유지
                                .trim()

                            if (cleanedText.isNotEmpty()) {
                                allSegments.add(TranscriptSegment(
                                    startTimeMs = segmentStart,
                                    endTimeMs = segmentEnd,
                                    text = cleanedText
                                ))
                            }
                        }
                    }
                }

                // 청크 파일 삭제
                try {
                    java.io.File(chunk.filePath).delete()
                } catch (e: Exception) {
                    logger.warn("청크 파일 삭제 실패: {}", chunk.filePath, e)
                }

                logger.info("청크 STT 처리 완료: file={}, segments={}", chunk.filePath, allSegments.size)
            }

            val fullText = textBuilder.toString().trim()
            val avgConfidence = if (confidenceCount > 0) totalConfidence / confidenceCount else null

            logger.info("청크 분할 처리 완료: textLength={}, segmentsCount={}, avgConfidence={}",
                fullText.length, allSegments.size, avgConfidence)

            TranscriptResult(
                text = fullText,
                segments = allSegments,
                language = sttLanguageCode,
                confidence = avgConfidence?.toFloat()
            )

        } catch (e: Exception) {
            logger.error("청크 분할 처리 실패: audioUrl={}", audioUrl, e)
            throw SttException("Failed to transcribe audio chunks", e)
        }
    }

    /**
     * 긴 오디오 파일 (60초 이상)에 대한 비동기 처리 (GCS URI 필요)
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
                    val segmentWords = mutableListOf<String>()
                    var wordCount = 0

                    words.forEach { wordInfo ->
                        val wordStartMs = wordInfo.startTime.seconds * 1000 +
                                wordInfo.startTime.nanos / 1_000_000
                        val wordEndMs = wordInfo.endTime.seconds * 1000 +
                                wordInfo.endTime.nanos / 1_000_000

                        // 세그먼트 구분: 2단어마다 또는 0.8초 이상 쉴 때
                        if (wordCount >= 2 || wordStartMs - segmentEnd > 800) {
                            if (segmentWords.isNotEmpty()) {
                                val cleanedText = segmentWords.joinToString(" ")  // 단어 사이 띄어쓰기 유지
                                    .trim()

                                if (cleanedText.isNotEmpty()) {
                                    segments.add(TranscriptSegment(
                                        startTimeMs = segmentStart,
                                        endTimeMs = segmentEnd,
                                        text = cleanedText
                                    ))
                                }
                            }
                            segmentStart = wordStartMs
                            segmentWords.clear()
                            wordCount = 0
                        }

                        // 단어 정리 후 추가 (특수문자만 제거, 띄어쓰기는 유지)
                        val cleanWord = wordInfo.word
                            .replace("▁", "")
                            .replace("_", "")
                            .replace(" ", "")
                            .trim()

                        if (cleanWord.isNotEmpty()) {
                            segmentWords.add(cleanWord)
                            wordCount++
                        }

                        segmentEnd = wordEndMs
                    }

                    // 마지막 세그먼트 추가
                    if (segmentWords.isNotEmpty()) {
                        val cleanedText = segmentWords.joinToString(" ")  // 단어 사이 띄어쓰기 유지
                            .trim()

                        if (cleanedText.isNotEmpty()) {
                            segments.add(TranscriptSegment(
                                startTimeMs = segmentStart,
                                endTimeMs = segmentEnd,
                                text = cleanedText
                            ))
                        }
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
        val builder = RecognitionConfig.newBuilder()
            .setEncoding(AudioEncoding.OGG_OPUS)  // Opus 코덱 (음성에 최적화, 작은 파일)
            .setSampleRateHertz(sampleRateHertz)
            .setLanguageCode(sttLanguageCode)
            .setEnableWordTimeOffsets(enableWordTimeOffsets)

        // 언어별 설정
        when {
            sttLanguageCode.startsWith("ja") -> {
                // 일본어: latest_long 모델이 인식률이 낮을 수 있음, 기본 모델 사용
                logger.debug("일본어 STT 설정: 기본 모델 사용")
                builder.setEnableAutomaticPunctuation(false)  // 일본어는 구두점 자동 추가 비활성화
            }
            sttLanguageCode.startsWith("ko") -> {
                // 한국어: latest_long 모델 사용
                builder.setModel("latest_long")
                builder.setEnableAutomaticPunctuation(true)
            }
            sttLanguageCode.startsWith("en") -> {
                // 영어: latest_long 모델 사용
                builder.setModel("latest_long")
                builder.setEnableAutomaticPunctuation(true)
            }
            else -> {
                // 기타 언어: 기본 설정
                builder.setEnableAutomaticPunctuation(true)
            }
        }

        return builder.build()
    }
}

/**
 * STT 예외
 */
class SttException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
