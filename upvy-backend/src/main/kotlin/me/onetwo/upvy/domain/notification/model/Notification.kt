package me.onetwo.upvy.domain.notification.model

import java.time.Instant
import java.util.UUID

/**
 * 알림 엔티티
 *
 * 사용자에게 발송되는 알림 정보를 저장합니다.
 * 알림 센터에서 조회하고, 읽음 처리 및 삭제가 가능합니다.
 *
 * @property id 알림 ID (자동 생성)
 * @property userId 알림 수신자 ID
 * @property type 알림 유형 (LIKE, COMMENT, REPLY, FOLLOW)
 * @property title 알림 제목
 * @property body 알림 본문
 * @property data 추가 데이터 (JSON 형식, 클릭 시 이동 정보 등)
 * @property isRead 읽음 여부
 * @property deliveryStatus 발송 상태 (PENDING, SENT, DELIVERED, FAILED, SKIPPED)
 * @property actorId 알림 발생 주체 ID (좋아요/팔로우한 사용자)
 * @property targetType 타겟 유형 (CONTENT, COMMENT, USER)
 * @property targetId 타겟 ID (콘텐츠 ID, 댓글 ID 등)
 * @property createdAt 생성 시각
 * @property createdBy 생성자 ID
 * @property updatedAt 수정 시각
 * @property updatedBy 수정자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class Notification(
    val id: Long? = null,
    val userId: UUID,
    val type: NotificationType,
    val title: String,
    val body: String,
    val data: String? = null,
    val isRead: Boolean = false,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,
    val actorId: UUID? = null,
    val targetType: NotificationTargetType? = null,
    val targetId: UUID? = null,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
