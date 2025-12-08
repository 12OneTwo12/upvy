package me.onetwo.upvy.crawler.client.stt

import me.onetwo.upvy.crawler.client.SttClient
import me.onetwo.upvy.crawler.domain.ContentLanguage
import me.onetwo.upvy.crawler.domain.TranscriptResult
import me.onetwo.upvy.crawler.domain.TranscriptSegment
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 테스트용 Mock STT 클라이언트
 *
 * 테스트 환경에서 실제 STT API 호출 없이 동작을 시뮬레이션합니다.
 */
@Component
@ConditionalOnProperty(name = ["ai.stt.provider"], havingValue = "mock")
class MockSttClient : SttClient {

    companion object {
        private val logger = LoggerFactory.getLogger(MockSttClient::class.java)
    }

    var transcribeResponse: TranscriptResult = TranscriptResult(
        text = """
            안녕하세요, 오늘은 프로그래밍의 기초에 대해 알아보겠습니다.

            첫 번째로, 변수란 데이터를 저장하는 공간입니다.
            변수에는 숫자, 문자열, 불리언 등 다양한 타입이 있습니다.

            두 번째로, 함수는 코드를 재사용하기 위한 블록입니다.
            함수를 사용하면 반복되는 코드를 줄일 수 있습니다.

            세 번째로, 조건문은 특정 조건에 따라 다른 코드를 실행합니다.
            if, else, when 등의 키워드를 사용합니다.

            마지막으로, 반복문은 같은 코드를 여러 번 실행합니다.
            for, while 등의 키워드를 사용합니다.

            이상으로 프로그래밍의 기초에 대한 설명을 마치겠습니다.
            시청해 주셔서 감사합니다.
        """.trimIndent(),
        segments = listOf(
            TranscriptSegment(0, 5000, "안녕하세요, 오늘은 프로그래밍의 기초에 대해 알아보겠습니다."),
            TranscriptSegment(5000, 15000, "첫 번째로, 변수란 데이터를 저장하는 공간입니다."),
            TranscriptSegment(15000, 25000, "변수에는 숫자, 문자열, 불리언 등 다양한 타입이 있습니다."),
            TranscriptSegment(25000, 35000, "두 번째로, 함수는 코드를 재사용하기 위한 블록입니다."),
            TranscriptSegment(35000, 45000, "함수를 사용하면 반복되는 코드를 줄일 수 있습니다.")
        ),
        language = "ko-KR",
        confidence = 0.95f
    )

    override suspend fun transcribe(audioUrl: String, language: ContentLanguage): TranscriptResult {
        logger.debug("MockSttClient.transcribe called: audioUrl={}, language={}", audioUrl, language)
        return transcribeResponse
    }
}
