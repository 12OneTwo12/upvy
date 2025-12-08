package me.onetwo.upvy.domain.analytics.repository

import me.onetwo.upvy.domain.analytics.dto.ViewHistoryDetail
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 사용자 시청 기록 레포지토리
 *
 * user_view_history 테이블에 대한 데이터베이스 액세스를 담당합니다.
 */
interface UserViewHistoryRepository {

    /**
     * 시청 기록 저장
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @param watchedDuration 시청 시간 (초)
     * @param completionRate 완료율 (0-100)
     * @return 저장 완료 신호
     */
    fun save(
        userId: UUID,
        contentId: UUID,
        watchedDuration: Int,
        completionRate: Int
    ): Mono<Void>

    /**
     * 사용자의 최근 시청 기록 조회 (카테고리 선호도 분석용)
     *
     * @param userId 사용자 ID
     * @param since 조회 시작 시각
     * @param limit 조회 개수
     * @return 시청한 콘텐츠 ID 목록
     */
    fun findRecentViewedContentIds(
        userId: UUID,
        since: Instant,
        limit: Int
    ): Flux<UUID>

    /**
     * 사용자의 최근 시청 기록 상세 정보 조회 (선호도 점수 계산용)
     *
     * 선호도 점수 계산에 필요한 시청 시간, 완료율, 시청 시각 정보를 포함합니다.
     *
     * @param userId 사용자 ID
     * @param since 조회 시작 시각
     * @param limit 조회 개수
     * @return 시청 기록 상세 정보 목록 (최신순 정렬)
     */
    fun findRecentViewHistoryDetails(
        userId: UUID,
        since: Instant,
        limit: Int
    ): Flux<ViewHistoryDetail>
}
