package me.onetwo.growsnap.crawler.client

import me.onetwo.growsnap.crawler.domain.ContentMetadata
import me.onetwo.growsnap.crawler.domain.Segment

/**
 * LLM (Large Language Model) 클라이언트 인터페이스
 *
 * 텍스트 분석, 핵심 구간 추출, 메타데이터 생성 등에 사용됩니다.
 * Vertex AI (Gemini), OpenAI (GPT-4), Claude 등 다양한 구현체로 교체 가능합니다.
 */
interface LlmClient {

    /**
     * 프롬프트에 대한 응답 생성
     *
     * @param prompt 입력 프롬프트
     * @return AI 응답 텍스트
     */
    suspend fun analyze(prompt: String): String

    /**
     * 영상 자막에서 학습 가치가 높은 핵심 구간 추출
     *
     * @param transcript 전체 자막 텍스트
     * @return 추출된 세그먼트 목록 (시작/종료 시간, 제목, 키워드)
     */
    suspend fun extractKeySegments(transcript: String): List<Segment>

    /**
     * 콘텐츠 메타데이터 자동 생성
     *
     * @param content 콘텐츠 내용
     * @return 제목, 설명, 태그, 카테고리, 난이도
     */
    suspend fun generateMetadata(content: String): ContentMetadata
}
