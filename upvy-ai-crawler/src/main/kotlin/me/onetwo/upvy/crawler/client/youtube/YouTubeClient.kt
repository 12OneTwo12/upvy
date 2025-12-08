package me.onetwo.upvy.crawler.client.youtube

import me.onetwo.upvy.crawler.domain.ContentLanguage
import me.onetwo.upvy.crawler.domain.VideoCandidate

/**
 * YouTube Data API 클라이언트 인터페이스
 *
 * CC 라이선스 비디오 검색 및 상세 정보 조회를 담당합니다.
 */
interface YouTubeClient {

    /**
     * CC 라이선스 비디오 검색
     *
     * @param query 검색어
     * @param maxResults 최대 결과 수 (기본값: 10)
     * @param language 검색 대상 언어 (기본값: 한국어)
     * @return CC 라이선스 비디오 후보 목록
     */
    suspend fun searchCcVideos(
        query: String,
        maxResults: Int = 10,
        language: ContentLanguage = ContentLanguage.KO
    ): List<VideoCandidate>

    /**
     * 비디오 상세 정보 조회
     *
     * @param videoId YouTube 비디오 ID
     * @return 비디오 후보 정보 (없으면 null)
     */
    suspend fun getVideoDetails(videoId: String): VideoCandidate?

    /**
     * 비디오가 CC 라이선스인지 확인
     *
     * @param videoId YouTube 비디오 ID
     * @return CC 라이선스 여부
     */
    suspend fun isCcLicensed(videoId: String): Boolean
}
