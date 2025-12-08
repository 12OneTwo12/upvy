package me.onetwo.upvy.crawler.domain.content

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 게시된 콘텐츠 Repository (백엔드 contents 테이블)
 */
@Repository
interface PublishedContentRepository : JpaRepository<PublishedContent, String>

/**
 * 게시된 콘텐츠 메타데이터 Repository (백엔드 content_metadata 테이블)
 */
@Repository
interface PublishedContentMetadataRepository : JpaRepository<PublishedContentMetadata, Long>

/**
 * 게시된 콘텐츠 인터랙션 Repository (백엔드 content_interactions 테이블)
 */
@Repository
interface PublishedContentInteractionRepository : JpaRepository<PublishedContentInteraction, Long>
