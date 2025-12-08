package me.onetwo.upvy.crawler.client.youtube

import me.onetwo.upvy.crawler.domain.ContentLanguage
import me.onetwo.upvy.crawler.domain.VideoCandidate
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component

/**
 * 테스트용 Mock YouTube 클라이언트
 *
 * 테스트 환경에서 실제 YouTube API 호출 없이 동작을 시뮬레이션합니다.
 */
@Component
@ConditionalOnMissingBean(YouTubeClientImpl::class)
class MockYouTubeClient : YouTubeClient {

    companion object {
        private val logger = LoggerFactory.getLogger(MockYouTubeClient::class.java)
    }

    var searchResponse: List<VideoCandidate> = listOf(
        VideoCandidate(
            videoId = "mock_video_001",
            title = "Mock Video: Kotlin Programming Best Practices",
            channelId = "mock_channel_001",
            channelTitle = "Mock Channel",
            description = "Learn Kotlin programming best practices in this comprehensive tutorial.",
            publishedAt = "2024-01-15T10:00:00Z",
            duration = "PT15M30S",
            thumbnailUrl = "https://example.com/thumbnail1.jpg",
            viewCount = 50000,
            likeCount = 2500
        ),
        VideoCandidate(
            videoId = "mock_video_002",
            title = "Mock Video: Spring Boot Tutorial",
            channelId = "mock_channel_002",
            channelTitle = "Mock Tech Channel",
            description = "Complete Spring Boot tutorial for beginners.",
            publishedAt = "2024-02-20T14:30:00Z",
            duration = "PT20M45S",
            thumbnailUrl = "https://example.com/thumbnail2.jpg",
            viewCount = 75000,
            likeCount = 4000
        )
    )

    override suspend fun searchCcVideos(
        query: String,
        maxResults: Int,
        language: ContentLanguage
    ): List<VideoCandidate> {
        logger.debug("MockYouTubeClient.searchCcVideos called: query={}, maxResults={}, language={}",
            query, maxResults, language.code)
        return searchResponse.take(maxResults)
    }

    override suspend fun getVideoDetails(videoId: String): VideoCandidate? {
        logger.debug("MockYouTubeClient.getVideoDetails called: videoId={}", videoId)
        return searchResponse.find { it.videoId == videoId }
    }

    override suspend fun isCcLicensed(videoId: String): Boolean {
        logger.debug("MockYouTubeClient.isCcLicensed called: videoId={}", videoId)
        return true  // Mock에서는 항상 CC 라이선스로 처리
    }
}
