package me.onetwo.upvy.crawler.domain.content

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 콘텐츠-태그 관계 Repository (content_tags 테이블)
 */
@Repository
interface ContentTagRepository : JpaRepository<ContentTag, Long> {

    /**
     * 콘텐츠 ID와 태그 ID로 관계 존재 여부 확인
     */
    fun existsByContentIdAndTagIdAndDeletedAtIsNull(contentId: String, tagId: Long): Boolean
}
