package me.onetwo.upvy.compose.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

/**
 * 자막 정보
 */
data class SubtitleInfo(
    /**
     * 시작 시간 (초)
     */
    val start: Double,

    /**
     * 종료 시간 (초)
     */
    val end: Double,

    /**
     * 자막 텍스트
     */
    @field:NotBlank(message = "text는 필수입니다")
    val text: String
)

/**
 * 합성 메타데이터 (JSON 파트로 전달)
 */
data class ComposeMetadata(
    /**
     * 영상 제목 (텍스트 오버레이에 사용 가능)
     */
    val title: String? = null,

    /**
     * 워터마크 표시 여부
     */
    val watermark: Boolean = true,

    /**
     * 콘텐츠 언어 (ko, en, ja)
     */
    val language: String = "ko",

    /**
     * 자막 목록
     */
    @field:Valid
    val subtitles: List<SubtitleInfo> = emptyList()
)

/**
 * 영상 합성 응답 DTO
 *
 * 바이너리 응답과 함께 헤더로 전달되는 메타데이터
 */
data class ComposeResponse(
    /**
     * 합성 작업 ID
     */
    val composeId: String,

    /**
     * 영상 길이 (초)
     */
    val duration: Double,

    /**
     * 해상도
     */
    val resolution: String = "1080x1920",

    /**
     * 파일 크기 (bytes)
     */
    val fileSize: Long
)
