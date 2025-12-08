package me.onetwo.upvy.crawler.client.video

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.File

/**
 * 테스트용 Mock yt-dlp 래퍼
 *
 * 테스트 환경에서 실제 다운로드 없이 동작을 시뮬레이션합니다.
 */
@Component
@Profile("test")
@Primary
class MockYtDlpWrapper : YtDlpWrapper {

    companion object {
        private val logger = LoggerFactory.getLogger(MockYtDlpWrapper::class.java)
    }

    var downloadSuccess: Boolean = true
    var mockOutputPath: String = "/tmp/mock_video.mp4"
    var mockVideoInfo: String = """
        {
            "id": "mock_video_001",
            "title": "Mock Video Title",
            "duration": 930,
            "channel": "Mock Channel"
        }
    """.trimIndent()

    override fun download(videoId: String): String {
        logger.debug("MockYtDlpWrapper.download called: videoId={}", videoId)

        if (!downloadSuccess) {
            throw YtDlpException("Mock download failed: videoId=$videoId")
        }

        // 테스트용 빈 파일 생성
        val mockFile = File(mockOutputPath)
        mockFile.parentFile?.mkdirs()
        if (!mockFile.exists()) {
            mockFile.createNewFile()
        }

        return mockOutputPath
    }

    override fun getVideoInfo(videoId: String): String? {
        logger.debug("MockYtDlpWrapper.getVideoInfo called: videoId={}", videoId)
        return mockVideoInfo
    }
}
