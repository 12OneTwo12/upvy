package me.onetwo.upvy.domain.tag.model

import java.time.Instant

/**
 * 태그 엔티티
 *
 * 콘텐츠에 부여되는 태그를 나타내며, 태그 인기도(usage_count)를 추적합니다.
 *
 * @property id 태그 고유 식별자 (Auto Increment)
 * @property name 태그 이름 (정규화된 형태, 예: "프로그래밍", "#Java")
 * @property normalizedName 검색용 정규화된 이름 (소문자, 공백 제거, 예: "프로그래밍", "java")
 * @property usageCount 사용 횟수 (인기도, 여러 콘텐츠에 사용될수록 증가)
 * @property createdAt 생성 시각 (UTC Instant)
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각 (UTC Instant)
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (UTC Instant, Soft Delete)
 */
data class Tag(
    val id: Long? = null,
    val name: String,
    val normalizedName: String,
    val usageCount: Int = 0,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
