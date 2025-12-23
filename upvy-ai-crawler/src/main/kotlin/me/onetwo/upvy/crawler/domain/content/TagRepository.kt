package me.onetwo.upvy.crawler.domain.content

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

/**
 * 태그 Repository (tags 테이블)
 */
@Repository
interface TagRepository : JpaRepository<Tag, Long> {

    /**
     * 정규화된 이름으로 태그 조회 (deleted_at IS NULL)
     */
    fun findByNormalizedNameAndDeletedAtIsNull(normalizedName: String): Optional<Tag>

    /**
     * 태그 사용 횟수 증가
     *
     * @param tagId 태그 ID
     * @return 업데이트된 행 수
     */
    @Modifying
    @Query("UPDATE Tag t SET t.usageCount = t.usageCount + 1 WHERE t.id = :tagId AND t.deletedAt IS NULL")
    fun incrementUsageCount(@Param("tagId") tagId: Long): Int
}
