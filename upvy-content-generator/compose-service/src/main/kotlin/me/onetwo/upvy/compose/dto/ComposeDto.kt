package me.onetwo.upvy.compose.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive

/**
 * 영상 합성 요청 DTO
 *
 * n8n에서 COMPOSE 단계에 호출하는 API 요청 형식
 */
data class ComposeRequest(
    /**
     * 비주얼 클립 목록 (순서대로 연결)
     */
    @field:NotEmpty(message = "clips는 비어있을 수 없습니다")
    @field:Valid
    val clips: List<ClipInfo>,

    /**
     * 오디오 정보 (나레이션)
     */
    @field:Valid
    val audio: AudioInfo,

    /**
     * 자막 목록
     */
    @field:Valid
    val subtitles: List<SubtitleInfo> = emptyList(),

    /**
     * 메타데이터 (제목, 워터마크 등)
     */
    @field:Valid
    val metadata: ComposeMetadata,

    /**
     * 출력 설정
     */
    @field:Valid
    val output: OutputConfig
)

/**
 * 클립 정보
 */
data class ClipInfo(
    /**
     * GCS URI (gs://bucket/path/to/clip.mp4)
     */
    @field:NotBlank(message = "gcsUri는 필수입니다")
    val gcsUri: String,

    /**
     * 클립 시작 시간 (초)
     */
    @field:Positive
    val startTime: Double = 0.0,

    /**
     * 클립 길이 (초)
     */
    @field:Positive
    val duration: Double
)

/**
 * 오디오 정보
 */
data class AudioInfo(
    /**
     * GCS URI (gs://bucket/path/to/audio.mp3)
     */
    @field:NotBlank(message = "gcsUri는 필수입니다")
    val gcsUri: String
)

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
 * 합성 메타데이터
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
    val language: String = "ko"
)

/**
 * 출력 설정
 */
data class OutputConfig(
    /**
     * 출력 GCS URI 접두사 (gs://bucket/output/)
     */
    @field:NotBlank(message = "gcsUri는 필수입니다")
    val gcsUri: String
)

/**
 * 영상 합성 응답 DTO
 */
data class ComposeResponse(
    /**
     * 합성 작업 ID
     */
    val composeId: String,

    /**
     * 최종 영상 GCS URI
     */
    val videoGcsUri: String,

    /**
     * 썸네일 GCS URI
     */
    val thumbnailGcsUri: String,

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
