package me.onetwo.growsnap.crawler.backoffice.repository

import me.onetwo.growsnap.crawler.backoffice.domain.BackofficeUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BackofficeUserRepository : JpaRepository<BackofficeUser, Long> {

    /**
     * 사용자명으로 조회
     */
    fun findByUsername(username: String): BackofficeUser?

    /**
     * 사용자명 존재 여부
     */
    fun existsByUsername(username: String): Boolean

    /**
     * 활성화된 사용자만 조회
     */
    fun findByEnabledTrue(): List<BackofficeUser>
}
