package me.onetwo.upvy.crawler.search

import me.onetwo.upvy.crawler.backoffice.domain.Category
import me.onetwo.upvy.crawler.domain.AiContentJobRepository
import me.onetwo.upvy.crawler.domain.ContentLanguage
import me.onetwo.upvy.crawler.domain.JobStatus
import me.onetwo.upvy.crawler.domain.SearchContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.Month

/**
 * 검색 컨텍스트 수집기 인터페이스
 *
 * 현재 트렌드, 시즌, 인기 콘텐츠를 분석하여
 * LLM에게 제공할 컨텍스트를 생성합니다.
 */
interface SearchContextCollector {

    /**
     * 검색 컨텍스트 수집
     *
     * @return 현재 상황을 반영한 검색 컨텍스트
     */
    suspend fun collect(): SearchContext
}

/**
 * 검색 컨텍스트 수집기 구현체
 */
@Component
class SearchContextCollectorImpl(
    private val aiContentJobRepository: AiContentJobRepository
) : SearchContextCollector {

    companion object {
        private val logger = LoggerFactory.getLogger(SearchContextCollectorImpl::class.java)

        // 시즌별 키워드
        private val SEASONAL_KEYWORDS = mapOf(
            Month.JANUARY to "새해 목표, 계획, 습관 만들기, 동기부여",
            Month.FEBRUARY to "겨울, 발렌타인, 자기 개발",
            Month.MARCH to "봄, 새 학기, 취업 준비",
            Month.APRIL to "봄, 꽃놀이, 야외 활동",
            Month.MAY to "가정의 달, 부모님, 어린이",
            Month.JUNE to "여름 준비, 다이어트, 휴가 계획",
            Month.JULY to "여름 휴가, 물놀이, 건강",
            Month.AUGUST to "휴가, 독서, 자기 계발",
            Month.SEPTEMBER to "가을, 새 학기, 취업",
            Month.OCTOBER to "할로윈, 가을 축제, 단풍",
            Month.NOVEMBER to "수능, 연말 준비, 블랙프라이데이",
            Month.DECEMBER to "크리스마스, 새해 목표, 한 해 마무리, 동기부여"
        )
    }

    override suspend fun collect(): SearchContext {
        logger.info("검색 컨텍스트 수집 시작")

        val today = LocalDate.now()
        val seasonalContext = SEASONAL_KEYWORDS[today.month]

        // 최근 게시된 콘텐츠 제목 (중복 방지용)
        val recentlyPublished = getRecentlyPublishedTitles()

        // 콘텐츠가 부족한 카테고리 분석
        val underrepresentedCategories = findUnderrepresentedCategories()

        // 인기 키워드 (향후 분석 데이터 기반으로 개선 예정)
        val popularKeywords = listOf(
            "심리학", "심리", "프로그래밍", "AI", "한국사", "역사", "동기부여", "언어", "영어", "공부", "수능", "자격증"
        )

        // 인기 태그 (향후 분석 데이터 기반으로 개선 예정)
        val topPerformingTags = listOf(
            "한국사", "심리학", "심리", "분석", "동기부여", "역사", "영어", "강의", "강좌", "AI", "공부"
        )

        val context = SearchContext(
            appCategories = Category.entries.map { it.name },
            popularKeywords = popularKeywords,
            topPerformingTags = topPerformingTags,
            seasonalContext = seasonalContext,
            recentlyPublished = recentlyPublished,
            underrepresentedCategories = underrepresentedCategories,
            targetLanguages = listOf(ContentLanguage.KO, ContentLanguage.EN, ContentLanguage.JA)
        )

        logger.info(
            "검색 컨텍스트 수집 완료: seasonalContext={}, underrepresentedCategories={}",
            seasonalContext,
            underrepresentedCategories
        )

        return context
    }

    /**
     * 최근 게시된 콘텐츠 제목 조회
     */
    private fun getRecentlyPublishedTitles(): List<String> {
        return try {
            aiContentJobRepository.findByStatus(JobStatus.PUBLISHED)
                .mapNotNull { it.generatedTitle ?: it.youtubeTitle }
                .take(20)
        } catch (e: Exception) {
            logger.warn("최근 게시 콘텐츠 조회 실패", e)
            emptyList()
        }
    }

    /**
     * 콘텐츠가 부족한 카테고리 찾기
     */
    private fun findUnderrepresentedCategories(): List<String> {
        return try {
            val publishedJobs = aiContentJobRepository.findByStatus(JobStatus.PUBLISHED)
            val categoryCounts = publishedJobs
                .mapNotNull { it.category }
                .groupingBy { it }
                .eachCount()

            // 평균 이하의 카테고리 찾기
            val average = if (categoryCounts.isNotEmpty()) {
                categoryCounts.values.average()
            } else {
                0.0
            }

            val underrepresented = Category.entries.map { it.name }.filter { category ->
                (categoryCounts[category] ?: 0) < average
            }

            // 최대 5개만 반환
            underrepresented.take(5)

        } catch (e: Exception) {
            logger.warn("카테고리 분석 실패", e)
            // 기본값: 다양한 카테고리 제안
            listOf("과학", "역사", "예술", "심리학", "재테크")
        }
    }
}
