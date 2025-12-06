package me.onetwo.growsnap.crawler.client

import me.onetwo.growsnap.crawler.domain.TranscriptResult

/**
 * STT (Speech-to-Text) 클라이언트 인터페이스
 *
 * 음성을 텍스트로 변환합니다.
 * Google STT (Chirp), OpenAI Whisper 등 다양한 구현체로 교체 가능합니다.
 */
interface SttClient {

    /**
     * 오디오 파일을 텍스트로 변환
     *
     * @param audioUrl 오디오 파일 URL (S3 presigned URL 등)
     * @return 변환된 텍스트와 타임스탬프 정보
     */
    suspend fun transcribe(audioUrl: String): TranscriptResult
}
