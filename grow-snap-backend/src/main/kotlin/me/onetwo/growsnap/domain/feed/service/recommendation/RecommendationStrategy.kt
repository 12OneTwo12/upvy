package me.onetwo.growsnap.domain.feed.service.recommendation

/**
 * 추천 전략 enum
 *
 * 요구사항 명세서 섹션 2.2.1의 추천 알고리즘을 구현합니다.
 *
 * @property percentage 전체 피드에서 차지하는 비율 (0.0 ~ 1.0)
 * @property description 전략 설명
 */
enum class RecommendationStrategy(val percentage: Double, val description: String) {
    /**
     * 협업 추천 (40%)
     * 사용자의 행동 데이터 기반으로 유사한 사용자가 좋아한 콘텐츠 추천
     */
    COLLABORATIVE(0.40, "협업 추천 - 유사 사용자 기반"),

    /**
     * 인기 콘텐츠 (30%)
     * 조회수, 좋아요 등 인터랙션이 많은 인기 콘텐츠 추천
     */
    POPULAR(0.30, "인기 콘텐츠 - 높은 인터랙션"),

    /**
     * 신규 콘텐츠 (10%)
     * 최근 업로드된 신규 콘텐츠 추천 (발견 가능성 향상)
     */
    NEW(0.10, "신규 콘텐츠 - 최근 업로드"),

    /**
     * 랜덤 콘텐츠 (20%)
     * 무작위 콘텐츠 추천 (다양성 확보)
     */
    RANDOM(0.20, "랜덤 콘텐츠 - 다양성 확보");

    companion object {
        /**
         * 전체 비율 합이 100%인지 검증
         */
        init {
            val totalPercentage = RecommendationStrategy.entries.toTypedArray().sumOf { it.percentage }
            require(totalPercentage == 1.0) {
                "Total percentage must be 1.0, but got $totalPercentage"
            }
        }

        /**
         * 각 전략별로 몇 개의 콘텐츠를 가져올지 계산
         *
         * @param totalLimit 전체 가져올 콘텐츠 수
         * @return 전략별 콘텐츠 수 맵
         */
        fun calculateLimits(totalLimit: Int): Map<RecommendationStrategy, Int> {
            val limits = mutableMapOf<RecommendationStrategy, Int>()
            var remaining = totalLimit

            // COLLABORATIVE, POPULAR, NEW는 비율대로 계산
            RecommendationStrategy.entries.dropLast(1).forEach { strategy ->
                val limit = (totalLimit * strategy.percentage).toInt()
                limits[strategy] = limit
                remaining -= limit
            }

            // RANDOM은 나머지 전부
            limits[RANDOM] = remaining

            return limits
        }
    }
}
