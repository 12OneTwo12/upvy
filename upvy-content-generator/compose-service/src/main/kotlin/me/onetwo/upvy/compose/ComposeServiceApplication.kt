package me.onetwo.upvy.compose

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Upvy Compose Service
 *
 * FFmpeg 기반 영상 합성 서비스
 * 비주얼 클립 + 오디오 + 자막 → 최종 숏폼 영상
 *
 * n8n 워크플로우에서 HTTP POST /api/v1/compose 로 호출됩니다.
 */
@SpringBootApplication
class ComposeServiceApplication

fun main(args: Array<String>) {
    runApplication<ComposeServiceApplication>(*args)
}
