package me.onetwo.growsnap.crawler.backoffice.service

import me.onetwo.growsnap.crawler.backoffice.repository.BackofficeUserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class BackofficeUserDetailsService(
    private val backofficeUserRepository: BackofficeUserRepository
) : UserDetailsService {

    companion object {
        private val logger = LoggerFactory.getLogger(BackofficeUserDetailsService::class.java)
    }

    @Transactional
    override fun loadUserByUsername(username: String): UserDetails {
        logger.debug("로그인 시도: username={}", username)

        val user = backofficeUserRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("사용자를 찾을 수 없습니다: $username")

        if (!user.enabled) {
            throw UsernameNotFoundException("비활성화된 계정입니다: $username")
        }

        // 마지막 로그인 시간 업데이트
        user.lastLoginAt = Instant.now()
        user.updatedAt = Instant.now()
        backofficeUserRepository.save(user)

        logger.info("로그인 성공: username={}, role={}", username, user.role)

        return User.builder()
            .username(user.username)
            .password(user.password)
            .authorities(SimpleGrantedAuthority("ROLE_${user.role.name}"))
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(false)
            .disabled(!user.enabled)
            .build()
    }
}
