package me.onetwo.upvy.compose.controller

import jakarta.validation.Valid
import me.onetwo.upvy.compose.dto.ComposeRequest
import me.onetwo.upvy.compose.dto.ComposeResponse
import me.onetwo.upvy.compose.service.ComposeService
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

/**
 * 영상 합성 API 컨트롤러
 *
 * n8n 워크플로우의 COMPOSE 단계에서 호출됩니다.
 * 비주얼 클립 + 오디오 + 자막을 합성하여 최종 숏폼 영상을 생성합니다.
 */
@RestController
@RequestMapping("/api/v1")
class ComposeController(
    private val composeService: ComposeService
) {
    /**
     * 영상 합성 API
     *
     * @param request 합성 요청 (클립, 오디오, 자막, 메타데이터)
     * @return 합성 결과 (GCS URI, 길이, 파일 크기)
     */
    @PostMapping("/compose")
    suspend fun compose(
        @Valid @RequestBody request: ComposeRequest
    ): ResponseEntity<ComposeResponse> {
        logger.info { "영상 합성 요청: clips=${request.clips.size}, subtitles=${request.subtitles.size}" }

        val response = composeService.compose(request)

        logger.info { "영상 합성 완료: composeId=${response.composeId}, duration=${response.duration}s" }

        return ResponseEntity.ok(response)
    }
}
