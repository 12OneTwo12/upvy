package me.onetwo.growsnap.domain.analytics.controller

import jakarta.validation.Valid
import me.onetwo.growsnap.domain.analytics.dto.ViewEventRequest
import me.onetwo.growsnap.domain.analytics.service.AnalyticsService
import me.onetwo.growsnap.infrastructure.security.util.toUserId
import org.springframework.http.HttpStatus
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * Analytics 컨트롤러
 *
 * 사용자의 콘텐츠 시청 이벤트를 추적합니다.
 *
 * **참고**: 좋아요, 저장, 공유 등의 인터랙션은 각 도메인 컨트롤러에서
 * Spring Event를 통해 비동기로 처리됩니다.
 * (예: POST /api/v1/contents/{id}/like)
 *
 * @property analyticsService Analytics 서비스
 */
@RestController
@RequestMapping(ApiPaths.API_V1_ANALYTICS)
class AnalyticsController(
    private val analyticsService: AnalyticsService
) {

    /**
     * 시청 이벤트 추적
     *
     * 사용자의 콘텐츠 시청 기록을 저장합니다.
     * 클라이언트는 영상 재생 중 또는 종료 시 시청 시간(watchedDuration)을 전송합니다.
     *
     * ### 비즈니스 규칙
     * - 스킵 이벤트 (skipped=true): 시청 기록만 저장, view_count 증가 안 함
     * - 정상 시청 (skipped=false): 시청 기록 저장 + view_count 증가
     * - watchedDuration: 실제 시청한 시간 (초 단위)
     * - completionRate: 시청 완료율 (0-100)
     *
     * ### 개인화 추천 활용
     * 시청 시간 데이터는 Item-based Collaborative Filtering 추천 시스템의
     * 사용자 선호도 분석에 활용됩니다.
     *
     * ### 향후 개선 계획 (Kafka 도입)
     * 현재는 동기 API로 시청 이벤트를 수신하고 있지만, 대용량 트래픽 처리를 위해
     * **향후 Kafka 비동기 이벤트 스트리밍으로 전환할 예정**입니다.
     * - 클라이언트 → Kafka Topic → Consumer → DB 저장
     * - 메시지 유실 방지 및 부하 분산
     * - 실시간 스트리밍 분석 가능
     *
     * @param userId 인증된 사용자 ID
     * @param request 시청 이벤트 요청 (contentId, watchedDuration, completionRate, skipped)
     * @return 204 No Content
     */
    @PostMapping("/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun trackViewEvent(
        principal: Mono<Principal>,
        @Valid @RequestBody request: ViewEventRequest
    ): Mono<Void> {
        return principal
            .toUserId()
            .flatMap { userId ->
                analyticsService.trackViewEvent(userId, request)
            }
    }
}
