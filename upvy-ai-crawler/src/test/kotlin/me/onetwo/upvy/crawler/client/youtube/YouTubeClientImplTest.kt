package me.onetwo.upvy.crawler.client.youtube

import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.SearchListResponse
import com.google.api.services.youtube.model.SearchResult
import com.google.api.services.youtube.model.ResourceId
import com.google.api.services.youtube.model.SearchResultSnippet
import com.google.api.services.youtube.model.Video
import com.google.api.services.youtube.model.VideoListResponse
import com.google.api.services.youtube.model.VideoSnippet
import com.google.api.services.youtube.model.VideoContentDetails
import com.google.api.services.youtube.model.VideoStatistics
import com.google.api.services.youtube.model.VideoStatus
import com.google.api.services.youtube.model.ThumbnailDetails
import com.google.api.services.youtube.model.Thumbnail
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigInteger

@DisplayName("YouTubeClientImpl 테스트")
class YouTubeClientImplTest {

    private lateinit var client: YouTubeClientImpl
    private lateinit var mockYouTube: YouTube
    private lateinit var mockSearch: YouTube.Search
    private lateinit var mockSearchList: YouTube.Search.List
    private lateinit var mockVideos: YouTube.Videos
    private lateinit var mockVideosList: YouTube.Videos.List

    @BeforeEach
    fun setUp() {
        mockYouTube = mockk(relaxed = true)
        mockSearch = mockk(relaxed = true)
        mockSearchList = mockk(relaxed = true)
        mockVideos = mockk(relaxed = true)
        mockVideosList = mockk(relaxed = true)

        every { mockYouTube.search() } returns mockSearch
        every { mockSearch.list(any()) } returns mockSearchList
        every { mockYouTube.videos() } returns mockVideos
        every { mockVideos.list(any()) } returns mockVideosList

        // YouTube.Builder 모킹
        mockkConstructor(YouTube.Builder::class)
        every { anyConstructed<YouTube.Builder>().setApplicationName(any()) } returns mockk {
            every { build() } returns mockYouTube
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    @DisplayName("searchCcVideos - CC 라이선스 비디오 검색")
    inner class SearchCcVideos {

        @Test
        @DisplayName("검색어가 주어지면 CC 비디오 목록을 반환한다")
        fun searchCcVideos_WithQuery_ReturnsCcVideos() = runBlocking {
            // Given: 검색 결과 모킹
            val searchResponse = createSearchResponse(listOf("vid1", "vid2", "vid3"))
            val videoResponse = createVideoResponse(
                listOf(
                    createVideo("vid1", "Kotlin Tutorial", "ch1", "Dev Channel", 10000, 500),
                    createVideo("vid2", "Python Basics", "ch2", "Coding School", 20000, 800),
                    createVideo("vid3", "Java Guide", "ch3", "Tech Academy", 15000, 600)
                )
            )

            every { mockSearchList.execute() } returns searchResponse
            every { mockVideosList.execute() } returns videoResponse

            client = createClient()

            // When: CC 비디오 검색
            val videos = client.searchCcVideos("programming tutorial", 10)

            // Then: 비디오 목록 반환
            assertThat(videos).hasSize(3)
            assertThat(videos[0].videoId).isEqualTo("vid1")
            assertThat(videos[0].title).isEqualTo("Kotlin Tutorial")
            assertThat(videos[0].channelId).isEqualTo("ch1")
            assertThat(videos[0].viewCount).isEqualTo(10000)
        }

        @Test
        @DisplayName("검색 결과가 없으면 빈 목록을 반환한다")
        fun searchCcVideos_WithNoResults_ReturnsEmptyList() = runBlocking {
            // Given: 빈 검색 결과
            val emptySearchResponse = SearchListResponse().apply {
                items = emptyList()
            }

            every { mockSearchList.execute() } returns emptySearchResponse

            client = createClient()

            // When: CC 비디오 검색
            val videos = client.searchCcVideos("nonexistent topic", 10)

            // Then: 빈 목록
            assertThat(videos).isEmpty()
        }

        @Test
        @DisplayName("API 오류 발생 시 예외를 던진다")
        fun searchCcVideos_WithApiError_ThrowsException() = runBlocking {
            // Given: API 오류
            every { mockSearchList.execute() } throws RuntimeException("API quota exceeded")

            client = createClient()

            // When & Then: 예외 발생
            assertThatThrownBy {
                runBlocking { client.searchCcVideos("test", 10) }
            }.isInstanceOf(YouTubeApiException::class.java)
        }
    }

    @Nested
    @DisplayName("getVideoDetails - 비디오 상세 정보 조회")
    inner class GetVideoDetails {

        @Test
        @DisplayName("비디오 ID가 주어지면 상세 정보를 반환한다")
        fun getVideoDetails_WithVideoId_ReturnsDetails() = runBlocking {
            // Given: 비디오 상세 정보 모킹
            val video = createVideo("vid123", "Test Video", "ch1", "Test Channel", 50000, 2500)
            val videoResponse = createVideoResponse(listOf(video))

            every { mockVideosList.execute() } returns videoResponse

            client = createClient()

            // When: 상세 정보 조회
            val details = client.getVideoDetails("vid123")

            // Then: 상세 정보 반환
            assertThat(details).isNotNull
            assertThat(details!!.videoId).isEqualTo("vid123")
            assertThat(details.title).isEqualTo("Test Video")
            assertThat(details.viewCount).isEqualTo(50000)
            assertThat(details.likeCount).isEqualTo(2500)
        }

        @Test
        @DisplayName("존재하지 않는 비디오 ID면 null을 반환한다")
        fun getVideoDetails_WithNonexistentId_ReturnsNull() = runBlocking {
            // Given: 빈 응답
            val emptyResponse = VideoListResponse().apply {
                items = emptyList()
            }

            every { mockVideosList.execute() } returns emptyResponse

            client = createClient()

            // When: 상세 정보 조회
            val details = client.getVideoDetails("nonexistent")

            // Then: null 반환
            assertThat(details).isNull()
        }
    }

    @Nested
    @DisplayName("isCcLicensed - CC 라이선스 확인")
    inner class IsCcLicensed {

        @Test
        @DisplayName("CC 라이선스 비디오면 true를 반환한다")
        fun isCcLicensed_WithCcVideo_ReturnsTrue() = runBlocking {
            // Given: CC 라이선스 비디오
            val video = Video().apply {
                id = "cc_video"
                status = VideoStatus().apply {
                    license = "creativeCommon"
                }
            }
            val response = VideoListResponse().apply {
                items = listOf(video)
            }

            every { mockVideosList.execute() } returns response

            client = createClient()

            // When: CC 라이선스 확인
            val isCc = client.isCcLicensed("cc_video")

            // Then: true
            assertThat(isCc).isTrue()
        }

        @Test
        @DisplayName("일반 라이선스 비디오면 false를 반환한다")
        fun isCcLicensed_WithStandardVideo_ReturnsFalse() = runBlocking {
            // Given: 일반 라이선스 비디오
            val video = Video().apply {
                id = "standard_video"
                status = VideoStatus().apply {
                    license = "youtube"
                }
            }
            val response = VideoListResponse().apply {
                items = listOf(video)
            }

            every { mockVideosList.execute() } returns response

            client = createClient()

            // When: CC 라이선스 확인
            val isCc = client.isCcLicensed("standard_video")

            // Then: false
            assertThat(isCc).isFalse()
        }

        @Test
        @DisplayName("존재하지 않는 비디오면 false를 반환한다")
        fun isCcLicensed_WithNonexistentVideo_ReturnsFalse() = runBlocking {
            // Given: 빈 응답
            val emptyResponse = VideoListResponse().apply {
                items = emptyList()
            }

            every { mockVideosList.execute() } returns emptyResponse

            client = createClient()

            // When: CC 라이선스 확인
            val isCc = client.isCcLicensed("nonexistent")

            // Then: false
            assertThat(isCc).isFalse()
        }
    }

    // ========== Helper Methods ==========

    private fun createClient(): YouTubeClientImpl {
        return YouTubeClientImpl(apiKey = "test-api-key")
    }

    private fun createSearchResponse(videoIds: List<String>): SearchListResponse {
        val searchResults = videoIds.map { videoId ->
            SearchResult().apply {
                id = ResourceId().apply {
                    this.videoId = videoId
                }
                snippet = SearchResultSnippet().apply {
                    title = "Video $videoId"
                    channelId = "ch_$videoId"
                }
            }
        }

        return SearchListResponse().apply {
            items = searchResults
        }
    }

    private fun createVideoResponse(videos: List<Video>): VideoListResponse {
        return VideoListResponse().apply {
            items = videos
        }
    }

    private fun createVideo(
        videoId: String,
        title: String,
        channelId: String,
        channelTitle: String,
        viewCount: Long,
        likeCount: Long
    ): Video {
        return Video().apply {
            id = videoId
            snippet = VideoSnippet().apply {
                this.title = title
                this.channelId = channelId
                this.channelTitle = channelTitle
                description = "Description for $title"
                thumbnails = ThumbnailDetails().apply {
                    high = Thumbnail().apply {
                        url = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    }
                }
            }
            contentDetails = VideoContentDetails().apply {
                duration = "PT10M30S"
            }
            statistics = VideoStatistics().apply {
                this.viewCount = BigInteger.valueOf(viewCount)
                this.likeCount = BigInteger.valueOf(likeCount)
            }
        }
    }
}
