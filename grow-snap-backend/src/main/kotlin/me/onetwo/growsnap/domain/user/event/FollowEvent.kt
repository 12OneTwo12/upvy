package me.onetwo.growsnap.domain.user.event

import java.time.LocalDateTime
import java.util.UUID

/**
 * 팔로우 이벤트
 *
 * 사용자가 다른 사용자를 팔로우했을 때 발생하는 이벤트입니다.
 * 이 이벤트는 메인 트랜잭션 커밋 후 비동기적으로 처리되어
 * 팔로우 알림을 생성하는 데 사용됩니다.
 *
 * ## 설계 이유
 *
 * ### Spring Event 패턴 사용 이유
 * 1. **성능 향상**: 메인 응답 시간 단축 (비동기 알림 처리)
 * 2. **낮은 결합도**: Follow 로직과 알림 로직 분리
 * 3. **장애 격리**: 알림 전송 실패해도 팔로우 요청은 성공
 * 4. **확장성**: 나중에 다른 이벤트 리스너 추가 가능 (예: 통계, 이메일 발송)
 *
 * ### 처리 흐름
 * ```
 * 1. 사용자 A가 사용자 B를 팔로우
 * 2. FollowService.follow() 호출
 * 3. follows 테이블에 팔로우 관계 저장 (메인 트랜잭션)
 * 4. user_profiles 테이블의 팔로워/팔로잉 수 증가
 * 5. FollowEvent 발행
 * 6. 트랜잭션 커밋
 * 7. FollowEventListener가 비동기로 알림 생성
 * ```
 *
 * @property followerId 팔로우한 사용자 ID
 * @property followingId 팔로우된 사용자 ID (알림 받을 사용자)
 * @property timestamp 이벤트 발생 시각
 */
data class FollowEvent(
    val followerId: UUID,
    val followingId: UUID,
    val timestamp: LocalDateTime = LocalDateTime.now()
)
