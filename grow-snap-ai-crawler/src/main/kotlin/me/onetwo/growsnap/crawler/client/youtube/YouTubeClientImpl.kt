package me.onetwo.growsnap.crawler.client.youtube

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.SearchListResponse
import com.google.api.services.youtube.model.VideoListResponse
import me.onetwo.growsnap.crawler.domain.ContentLanguage
import me.onetwo.growsnap.crawler.domain.VideoCandidate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * YouTube Data API v3 클라이언트 구현체
 *
 * CC 라이선스 비디오를 검색하고 상세 정보를 조회합니다.
 */
@Component
@ConditionalOnProperty(name = ["youtube.api-key"])
class YouTubeClientImpl(
    @Value("\${youtube.api-key}") private val apiKey: String
) : YouTubeClient {

    companion object {
        private val logger = LoggerFactory.getLogger(YouTubeClientImpl::class.java)
        private const val APPLICATION_NAME = "GrowSnap-AI-Crawler"
    }

    private val youtube: YouTube by lazy {
        YouTube.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            null
        )
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    override suspend fun searchCcVideos(
        query: String,
        maxResults: Int,
        language: ContentLanguage
    ): List<VideoCandidate> {
        logger.info("YouTube CC 비디오 검색 시작: query={}, maxResults={}, language={}",
            query, maxResults, language.code)

        try {
            val searchRequest = youtube.search().list(listOf("id", "snippet"))
            searchRequest.key = apiKey
            searchRequest.q = query
            searchRequest.type = listOf("video")
            searchRequest.videoLicense = "creativeCommon"  // CC 라이선스만
            searchRequest.videoDuration = "medium"  // 4-20분 영상
            searchRequest.relevanceLanguage = language.code  // 타겟 언어
            searchRequest.maxResults = maxResults.toLong()

            val response: SearchListResponse = searchRequest.execute()

            val videoIds = response.items?.map { it.id.videoId } ?: emptyList()

            if (videoIds.isEmpty()) {
                logger.info("검색 결과 없음: query={}, language={}", query, language.code)
                return emptyList()
            }

            // 상세 정보 조회 (조회수, 좋아요 수 등)
            val candidates = getVideosDetails(videoIds)

            logger.info("검색 완료: query={}, language={}, resultCount={}",
                query, language.code, candidates.size)
            return candidates

        } catch (e: Exception) {
            logger.error("YouTube 검색 실패: query={}, language={}", query, language.code, e)
            throw YouTubeApiException("Failed to search YouTube videos", e)
        }
    }

    override suspend fun getVideoDetails(videoId: String): VideoCandidate? {
        logger.debug("비디오 상세 정보 조회: videoId={}", videoId)

        try {
            val details = getVideosDetails(listOf(videoId))
            return details.firstOrNull()
        } catch (e: Exception) {
            logger.error("비디오 상세 정보 조회 실패: videoId={}", videoId, e)
            return null
        }
    }

    override suspend fun isCcLicensed(videoId: String): Boolean {
        logger.debug("CC 라이선스 확인: videoId={}", videoId)

        try {
            val request = youtube.videos().list(listOf("status"))
            request.key = apiKey
            request.id = listOf(videoId)

            val response: VideoListResponse = request.execute()
            val video = response.items?.firstOrNull() ?: return false

            val isCc = video.status?.license == "creativeCommon"
            logger.debug("CC 라이선스 확인 결과: videoId={}, isCc={}", videoId, isCc)

            return isCc
        } catch (e: Exception) {
            logger.error("CC 라이선스 확인 실패: videoId={}", videoId, e)
            return false
        }
    }

    /**
     * 여러 비디오의 상세 정보를 한 번에 조회
     */
    private fun getVideosDetails(videoIds: List<String>): List<VideoCandidate> {
        if (videoIds.isEmpty()) return emptyList()

        val request = youtube.videos().list(listOf("snippet", "contentDetails", "statistics"))
        request.key = apiKey
        request.id = videoIds

        val response: VideoListResponse = request.execute()

        return response.items?.map { video ->
            VideoCandidate(
                videoId = video.id,
                title = video.snippet?.title ?: "",
                channelId = video.snippet?.channelId ?: "",
                channelTitle = video.snippet?.channelTitle,
                description = video.snippet?.description,
                publishedAt = video.snippet?.publishedAt?.toStringRfc3339(),
                duration = video.contentDetails?.duration,
                thumbnailUrl = video.snippet?.thumbnails?.high?.url
                    ?: video.snippet?.thumbnails?.medium?.url,
                viewCount = video.statistics?.viewCount?.toLong(),
                likeCount = video.statistics?.likeCount?.toLong()
            )
        } ?: emptyList()
    }
}

/**
 * YouTube API 예외
 */
class YouTubeApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
