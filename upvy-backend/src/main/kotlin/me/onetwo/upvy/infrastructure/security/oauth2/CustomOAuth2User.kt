package me.onetwo.upvy.infrastructure.security.oauth2

import me.onetwo.upvy.domain.user.model.UserRole
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User
import java.io.Serializable
import java.util.UUID

/**
 * 커스텀 OAuth2 사용자
 *
 * Spring Security의 OAuth2User를 확장하여
 * 데이터베이스의 사용자 ID, 이메일, 역할 정보를 포함합니다.
 *
 * Redis 세션에 저장되므로 Serializable을 구현합니다.
 *
 * @property oauth2User 기본 OAuth2User 정보
 * @property userId 데이터베이스 사용자 ID
 * @property email 사용자 이메일
 * @property role 사용자 역할
 */
class CustomOAuth2User(
    private val oauth2User: OAuth2User,
    val userId: UUID,
    val email: String,
    val role: UserRole
) : OAuth2User, Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }

    /**
     * OAuth2 속성 반환
     *
     * @return Map<String, Any> OAuth2 속성
     */
    override fun getAttributes(): Map<String, Any> = oauth2User.attributes

    /**
     * 권한 목록 반환
     *
     * 데이터베이스의 사용자 역할(USER, ADMIN)을 Spring Security 권한으로 변환하여 반환합니다.
     *
     * @return Collection<GrantedAuthority> 권한 목록
     */
    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_$role"))
    }

    /**
     * 사용자 이름 반환
     *
     * OAuth2에서 제공하는 이름 속성을 반환합니다.
     *
     * @return String 사용자 이름
     */
    override fun getName(): String = oauth2User.name
}
