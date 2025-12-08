package me.onetwo.upvy.crawler.domain

/**
 * YouTube 비디오 후보
 *
 * YouTube Data API에서 검색된 CC 라이선스 비디오 정보
 */
data class VideoCandidate(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelTitle: String? = null,
    val description: String? = null,
    val publishedAt: String? = null,
    val duration: String? = null,  // ISO 8601 duration (PT1H2M3S)
    val thumbnailUrl: String? = null,
    val viewCount: Long? = null,
    val likeCount: Long? = null
)

/**
 * 검색 컨텍스트
 *
 * LLM이 검색 쿼리를 생성할 때 참고하는 현재 상황 정보
 */
data class SearchContext(
    val appCategories: List<String>,
    val popularKeywords: List<String>,
    val topPerformingTags: List<String>,
    val seasonalContext: String?,
    val recentlyPublished: List<String>,
    val underrepresentedCategories: List<String>,
    val targetLanguages: List<ContentLanguage> = listOf(ContentLanguage.KO, ContentLanguage.EN, ContentLanguage.JA)
)

/**
 * 콘텐츠 언어
 *
 * 글로벌 앱 지원을 위한 언어 구분
 */
enum class ContentLanguage(
    val code: String,           // YouTube API relevanceLanguage
    val displayName: String,    // 표시명
    val nativeName: String      // 해당 언어로 된 이름
) {
    KO("ko", "Korean", "한국어"),
    EN("en", "English", "English"),
    JA("ja", "Japanese", "日本語");

    companion object {
        fun fromCode(code: String): ContentLanguage? =
            entries.find { it.code.equals(code, ignoreCase = true) }
    }
}

/**
 * AI 생성 검색 쿼리
 *
 * LLM이 생성한 YouTube 검색어
 */
data class SearchQuery(
    val query: String,
    val targetCategory: String,
    val expectedContentType: String,
    val priority: Int,
    val language: ContentLanguage = ContentLanguage.KO  // 검색어 언어
)

/**
 * LLM 비디오 평가 결과
 *
 * 메타데이터 기반으로 비디오의 품질을 사전 평가한 결과
 */
data class EvaluatedVideo(
    val candidate: VideoCandidate,
    val relevanceScore: Int,
    val educationalValue: Int,
    val shortFormSuitability: Int,  // 숏폼 적합성 (0-100): 빠른 템포, 편집 밀도, 콘텐츠 압축도
    val predictedQuality: Int,
    val recommendation: Recommendation,
    val reasoning: String,
    val language: ContentLanguage = ContentLanguage.KO  // 콘텐츠 언어
)

/**
 * 비디오 추천 등급
 */
enum class Recommendation {
    HIGHLY_RECOMMENDED,
    RECOMMENDED,
    MAYBE,
    SKIP
}

/**
 * 품질 점수
 */
data class QualityScore(
    val totalScore: Int,
    val contentRelevance: Int,
    val audioClarity: Int,
    val visualQuality: Int,
    val educationalValue: Int
)

/**
 * 검토 우선순위
 */
enum class ReviewPriority {
    HIGH,
    NORMAL,
    LOW
}

/**
 * 핵심 구간 (세그먼트)
 *
 * LLM이 추출한 학습 가치가 높은 비디오 구간
 */
data class Segment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val title: String,
    val description: String? = null,
    val keywords: List<String> = emptyList()
)

/**
 * 음성-텍스트 변환 결과
 */
data class TranscriptResult(
    val text: String,
    val segments: List<TranscriptSegment> = emptyList(),
    val language: String? = null,
    val confidence: Float? = null
)

/**
 * 자막 세그먼트 (타임스탬프 포함)
 */
data class TranscriptSegment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

/**
 * AI가 생성한 콘텐츠 메타데이터
 */
data class ContentMetadata(
    val title: String,
    val description: String,
    val tags: List<String>,
    val category: String,
    val difficulty: Difficulty,
    val sourceAttribution: String? = null  // 출처 표기 (유튜브 원본 정보)
)

/**
 * AI 콘텐츠 세그먼트 엔티티
 *
 * 한 영상에서 추출된 여러 클립 정보
 */
data class AiContentSegment(
    val id: Long? = null,
    val jobId: Long,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val title: String? = null,
    val description: String? = null,
    val keywords: List<String> = emptyList(),
    val s3Key: String? = null,
    val thumbnailS3Key: String? = null,
    val qualityScore: Int? = null,
    val isSelected: Boolean = false
)
