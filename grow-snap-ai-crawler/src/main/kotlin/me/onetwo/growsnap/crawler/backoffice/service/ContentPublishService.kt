package me.onetwo.growsnap.crawler.backoffice.service

import me.onetwo.growsnap.crawler.backoffice.repository.PendingContentRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * 콘텐츠 게시 서비스
 *
 * 승인된 콘텐츠를 백엔드 contents/content_metadata 테이블에 INSERT합니다.
 */
@Service
class ContentPublishService(
    private val pendingContentRepository: PendingContentRepository,
    @Value("\${crawler.system-user-id:00000000-0000-0000-0000-000000000001}")
    private val systemUserId: String
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ContentPublishService::class.java)
    }

    /**
     * 콘텐츠 게시
     *
     * pending_contents의 콘텐츠를 백엔드 DB의 contents 테이블에 INSERT합니다.
     * 현재는 별도 DB 연결 없이 UUID만 생성하여 반환합니다.
     * 실제 구현 시 백엔드 DB 연결 또는 API 호출로 대체됩니다.
     *
     * @param pendingContentId 승인 대기 콘텐츠 ID
     * @return 생성된 contents.id (UUID)
     */
    @Transactional
    fun publishContent(pendingContentId: Long): String {
        val pendingContent = pendingContentRepository.findById(pendingContentId)
            .orElseThrow { IllegalArgumentException("콘텐츠를 찾을 수 없습니다: id=$pendingContentId") }

        logger.info("콘텐츠 게시 시작: pendingContentId={}, title={}", pendingContentId, pendingContent.title)

        // TODO: 백엔드 DB에 실제 INSERT 구현
        // 현재는 UUID만 생성하여 반환
        val publishedContentId = UUID.randomUUID().toString()

        logger.info(
            "콘텐츠 게시 완료: pendingContentId={}, publishedContentId={}",
            pendingContentId, publishedContentId
        )

        return publishedContentId
    }
}
