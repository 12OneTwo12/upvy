package me.onetwo.upvy.domain.feed.dto

/**
 * 자막 정보 응답 DTO
 *
 * 콘텐츠의 자막 정보입니다.
 *
 * @property language 언어 코드 (ISO 639-1, 예: ko, en)
 * @property subtitleUrl 자막 파일 URL (VTT 형식)
 */
data class SubtitleInfoResponse(
    val language: String,
    val subtitleUrl: String
)
