package me.onetwo.upvy.compose.controller

import com.fasterxml.jackson.databind.ObjectMapper
import me.onetwo.upvy.compose.dto.ComposeMetadata
import me.onetwo.upvy.compose.service.ComposeService
import mu.KotlinLogging
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.FileInputStream

private val logger = KotlinLogging.logger {}

/**
 * 영상 합성 API 컨트롤러
 *
 * n8n 워크플로우의 COMPOSE 단계에서 호출됩니다.
 * 비주얼 클립 + 오디오 + 자막을 합성하여 최종 숏폼 영상을 생성합니다.
 *
 * 입력: Multipart/form-data
 * - clips: 비디오 파일들 (복수 가능)
 * - audio: 오디오 파일 (mp3/wav)
 * - metadata: JSON (자막, 제목, 워터마크 설정 등)
 *
 * 출력: 합성된 영상 바이너리 (video/mp4)
 * - X-Compose-Id 헤더: 합성 작업 ID
 * - X-Duration 헤더: 영상 길이 (초)
 * - X-Resolution 헤더: 해상도
 * - Content-Length 헤더: 파일 크기
 */
@RestController
@RequestMapping("/api/v1")
class ComposeController(
    private val composeService: ComposeService,
    private val objectMapper: ObjectMapper
) {
    /**
     * 영상 합성 API
     *
     * @param clips 비주얼 클립 파일들
     * @param audio 오디오 파일
     * @param metadata 메타데이터 JSON (자막, 워터마크 등)
     * @return 합성된 영상 바이너리
     */
    @PostMapping("/compose", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun compose(
        @RequestPart("clips") clips: List<MultipartFile>,
        @RequestPart("audio") audio: MultipartFile,
        @RequestPart("metadata") metadataJson: String
    ): ResponseEntity<InputStreamResource> {
        logger.info { "영상 합성 요청: clips=${clips.size}개, audio=${audio.originalFilename}" }

        // 메타데이터 파싱
        val metadata = objectMapper.readValue(metadataJson, ComposeMetadata::class.java)
        logger.debug { "메타데이터: subtitles=${metadata.subtitles.size}개, watermark=${metadata.watermark}" }

        // 합성 실행
        val result = composeService.compose(clips, audio, metadata)

        logger.info { "영상 합성 완료: composeId=${result.composeId}, duration=${result.duration}s, size=${result.fileSize}" }

        // 바이너리 응답
        val videoStream = FileInputStream(result.videoFile)
        val resource = InputStreamResource(videoStream)

        return ResponseEntity.ok()
            .header("X-Compose-Id", result.composeId)
            .header("X-Duration", result.duration.toString())
            .header("X-Resolution", "1080x1920")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${result.composeId}.mp4\"")
            .contentLength(result.fileSize)
            .contentType(MediaType.parseMediaType("video/mp4"))
            .body(resource)
    }

    /**
     * 썸네일 포함 영상 합성 API
     *
     * 영상과 썸네일을 ZIP으로 반환
     */
    @PostMapping("/compose-with-thumbnail", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun composeWithThumbnail(
        @RequestPart("clips") clips: List<MultipartFile>,
        @RequestPart("audio") audio: MultipartFile,
        @RequestPart("metadata") metadataJson: String
    ): ResponseEntity<InputStreamResource> {
        logger.info { "영상+썸네일 합성 요청: clips=${clips.size}개" }

        val metadata = objectMapper.readValue(metadataJson, ComposeMetadata::class.java)
        val result = composeService.composeWithThumbnail(clips, audio, metadata)

        logger.info { "영상+썸네일 합성 완료: composeId=${result.composeId}" }

        val zipStream = FileInputStream(result.zipFile)
        val resource = InputStreamResource(zipStream)

        return ResponseEntity.ok()
            .header("X-Compose-Id", result.composeId)
            .header("X-Duration", result.duration.toString())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${result.composeId}.zip\"")
            .contentLength(result.zipFile.length())
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(resource)
    }
}
