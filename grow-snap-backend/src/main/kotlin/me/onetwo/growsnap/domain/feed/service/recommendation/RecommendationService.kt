package me.onetwo.growsnap.domain.feed.service.recommendation

import me.onetwo.growsnap.domain.content.model.Category
import reactor.core.publisher.Flux
import java.util.UUID

/**
 * 추천 서비스 인터페이스
 *
 * 콘텐츠 추천 알고리즘을 제공합니다.
 */
interface RecommendationService {

    /**
     * 추천 콘텐츠 ID 목록 조회
     *
     * 여러 추천 전략을 혼합하여 콘텐츠 ID 목록을 반환합니다.
     * 언어 가중치를 적용하여 점수 기반 정렬을 수행합니다.
     *
     * ### 추천 전략 (비율)
     * - 협업 필터링 (40%): Item-based Collaborative Filtering
     * - 인기 콘텐츠 (30%): 높은 인터랙션
     * - 신규 콘텐츠 (10%): 최근 업로드
     * - 랜덤 콘텐츠 (20%): 다양성 확보
     *
     * Issue #107: 언어 기반 가중치 적용
     * - 사용자 선호 언어와 일치하는 콘텐츠: 2.0x 가중치
     * - 사용자 선호 언어와 불일치하는 콘텐츠: 0.5x 가중치
     *
     * @param userId 사용자 ID
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @param preferredLanguage 사용자 선호 언어 (ISO 639-1, 예: ko, en) - 기본값: "en"
     * @param category 필터링할 카테고리 (null이면 전체 피드)
     * @return 추천 콘텐츠 ID 목록 (점수 기반 정렬)
     */
    fun getRecommendedContentIds(
        userId: UUID,
        limit: Int,
        excludeContentIds: List<UUID>,
        preferredLanguage: String = "en",
        category: Category? = null
    ): Flux<UUID>
}
