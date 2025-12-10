package me.onetwo.upvy.crawler.client

import me.onetwo.upvy.crawler.domain.ContentLanguage
import me.onetwo.upvy.crawler.domain.ContentMetadata
import me.onetwo.upvy.crawler.domain.EditPlan
import me.onetwo.upvy.crawler.domain.EvaluatedVideo
import me.onetwo.upvy.crawler.domain.SearchContext
import me.onetwo.upvy.crawler.domain.SearchQuery
import me.onetwo.upvy.crawler.domain.Segment
import me.onetwo.upvy.crawler.domain.VideoCandidate

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
     * 영상 자막에서 숏폼 편집 계획 생성
     *
     * 단순 구간 추출이 아니라, 여러 구간을 어떻게 조합하면
     * 최적의 숏폼이 되는지 전체 편집 계획을 제시합니다.
     * 스토리 플로우, 편집 전략, 클립 순서 등을 포함합니다.
     *
     * @param transcript 전체 자막 텍스트 (타임스탬프 포함)
     * @return 편집 계획 (클립 목록, 순서, 전략)
     */
    suspend fun generateEditPlan(transcript: String): EditPlan

    /**
     * 콘텐츠 메타데이터 자동 생성
     *
     * @param content 콘텐츠 내용
     * @param language 생성할 메타데이터 언어 (기본: 한국어)
     * @return 제목, 설명, 태그, 카테고리, 난이도
     */
    suspend fun generateMetadata(
        content: String,
        language: ContentLanguage = ContentLanguage.KO
    ): ContentMetadata

    /**
     * AI 기반 검색 쿼리 생성
     *
     * 현재 상황(트렌드, 시즌, 카테고리 등)을 분석하여
     * 양질의 교육 콘텐츠를 찾기 위한 검색어를 생성합니다.
     *
     * @param context 검색 컨텍스트 (앱 카테고리, 인기 키워드, 부족한 카테고리 등)
     * @return 생성된 검색 쿼리 목록 (우선순위 순)
     */
    suspend fun generateSearchQueries(context: SearchContext): List<SearchQuery>

    /**
     * 비디오 후보 사전 평가
     *
     * 실제 다운로드 전에 메타데이터만으로 비디오의 품질을 평가하여
     * API 쿼터와 처리 시간을 절약합니다.
     *
     * @param candidates 평가할 비디오 후보 목록
     * @return 평가 결과 (관련성 점수, 교육적 가치, 추천 등급)
     */
    suspend fun evaluateVideos(candidates: List<VideoCandidate>): List<EvaluatedVideo>
}
