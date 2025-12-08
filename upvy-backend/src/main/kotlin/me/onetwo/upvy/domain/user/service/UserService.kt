package me.onetwo.upvy.domain.user.service

import me.onetwo.upvy.domain.user.model.OAuthProvider
import me.onetwo.upvy.domain.user.model.User
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 사용자 관리 서비스 인터페이스
 *
 * 사용자 인증, 등록, 조회 등의 비즈니스 로직을 정의합니다.
 */
interface UserService {

    /**
     * OAuth 제공자와 Provider ID로 사용자 조회 또는 생성
     *
     * OAuth 로그인 시 사용하며, 기존 사용자가 있으면 반환하고 없으면 새로 생성합니다.
     * 신규 사용자 생성 시 프로필도 자동으로 생성됩니다.
     *
     * @param email 사용자 이메일
     * @param provider OAuth 제공자 (GOOGLE, NAVER, KAKAO 등)
     * @param providerId OAuth 제공자에서 제공한 사용자 고유 ID
     * @param name OAuth 제공자에서 제공한 사용자 이름 (닉네임으로 사용)
     * @param profileImageUrl OAuth 제공자에서 제공한 프로필 이미지 URL
     * @return 조회되거나 생성된 사용자
     */
    fun findOrCreateOAuthUser(
        email: String,
        provider: OAuthProvider,
        providerId: String,
        name: String?,
        profileImageUrl: String?
    ): Mono<User>

    /**
     * 사용자 ID로 조회
     *
     * @param userId 사용자 ID
     * @return 사용자 정보
     * @throws me.onetwo.upvy.domain.user.exception.UserNotFoundException 사용자를 찾을 수 없는 경우
     */
    fun getUserById(userId: UUID): Mono<User>

    /**
     * 이메일로 사용자 조회
     *
     * @param email 이메일
     * @return 사용자 정보
     * @throws me.onetwo.upvy.domain.user.exception.UserNotFoundException 사용자를 찾을 수 없는 경우
     */
    fun getUserByEmail(email: String): Mono<User>

    /**
     * 사용자 이메일 중복 확인
     *
     * @param email 확인할 이메일
     * @return 중복 여부 (true: 중복, false: 사용 가능)
     */
    fun isEmailDuplicated(email: String): Mono<Boolean>

    /**
     * 사용자 회원 탈퇴 (Soft Delete)
     *
     * 사용자, 프로필, 팔로우 관계를 모두 soft delete 처리합니다.
     *
     * ### 처리 내용
     * 1. 사용자 정보 soft delete
     * 2. 프로필 정보 soft delete
     * 3. 팔로우/팔로잉 관계 soft delete (양방향 모두)
     *
     * @param userId 탈퇴할 사용자 ID
     * @throws UserNotFoundException 사용자를 찾을 수 없는 경우
     */
    fun withdrawUser(userId: UUID): Mono<Void>
}
