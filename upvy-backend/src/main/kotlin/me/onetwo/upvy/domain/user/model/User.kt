package me.onetwo.upvy.domain.user.model

import java.time.Instant
import java.util.UUID

/**
 * 사용자 도메인 모델
 *
 * 계정 통합 아키텍처 (Account Linking)를 사용합니다.
 * - 사용자의 핵심 정보만 관리 (email, role, status)
 * - 인증 수단은 user_authentication_methods 테이블에서 관리
 * - 하나의 이메일로 여러 인증 수단 연결 가능 (Google + Email 등)
 *
 * @property id 사용자 ID (UUID)
 * @property email 이메일 주소 (고유)
 * @property role 사용자 권한
 * @property status 사용자 상태
 * @property createdAt 생성 시각
 * @property createdBy 생성자 ID
 * @property updatedAt 수정 시각
 * @property updatedBy 수정자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class User(
    val id: UUID? = null,
    val email: String,
    val role: UserRole = UserRole.USER,
    val status: UserStatus = UserStatus.ACTIVE,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
) {
    /**
     * 삭제된 사용자 여부 확인
     */
    fun isDeleted(): Boolean = status == UserStatus.DELETED

    /**
     * 활성 사용자 여부 확인
     */
    fun isActive(): Boolean = status == UserStatus.ACTIVE

    /**
     * 정지된 사용자 여부 확인
     */
    fun isSuspended(): Boolean = status == UserStatus.SUSPENDED
}

/**
 * 인증 제공자
 *
 * @property EMAIL 이메일 가입/로그인 (Apple App Store 심사 요구사항)
 * @property GOOGLE Google OAuth
 * @property NAVER Naver OAuth
 * @property KAKAO Kakao OAuth
 * @property SYSTEM 시스템 계정 (AI 크롤러 등)
 */
enum class OAuthProvider {
    EMAIL,   // 이메일 가입/로그인
    GOOGLE,
    NAVER,
    KAKAO,
    SYSTEM  // AI 크롤러 등 시스템 계정용
}

enum class UserRole {
    USER,
    ADMIN
}

/**
 * 사용자 계정 상태
 *
 * - ACTIVE: 활성 계정 (정상 사용 가능)
 * - DELETED: 탈퇴한 계정 (재가입 시 복원 가능)
 * - SUSPENDED: 정지된 계정 (관리자 제재)
 */
enum class UserStatus {
    ACTIVE,
    DELETED,
    SUSPENDED
}
