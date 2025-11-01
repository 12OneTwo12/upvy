package me.onetwo.growsnap.domain.content.event

import me.onetwo.growsnap.domain.analytics.service.ContentInteractionService
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_INTERACTIONS
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * ContentEventHandler 통합 테스트
 *
 * ContentCreatedEvent 이벤트 처리 및 ContentInteraction 생성을 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("콘텐츠 이벤트 핸들러 통합 테스트")
class ContentEventHandlerTest {

    @Autowired
    private lateinit var contentEventHandler: ContentEventHandler

    @Autowired
    private lateinit var contentInteractionService: ContentInteractionService

    @Autowired
    private lateinit var dslContext: DSLContext

    @Test
    @DisplayName("ContentCreatedEvent를 처리하고 ContentInteraction을 생성한다")
    fun handleContentCreated_CreatesContentInteraction() {
        // Given: 콘텐츠 ID와 생성자 ID
        val contentId = UUID.randomUUID()
        val creatorId = UUID.randomUUID()

        // 콘텐츠 생성 이벤트
        val event = ContentCreatedEvent(
            contentId = contentId,
            creatorId = creatorId
        )

        // 먼저 CONTENTS 테이블에 레코드 생성 (FK 제약조건)
        dslContext.execute(
            """
            INSERT INTO CONTENTS (ID, CREATOR_ID, CONTENT_TYPE, URL, THUMBNAIL_URL, WIDTH, HEIGHT, STATUS, CREATED_AT, UPDATED_AT)
            VALUES (?, ?, 'VIDEO', 'https://example.com/test.mp4', 'https://example.com/thumb.jpg', 1920, 1080, 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            contentId.toString(),
            creatorId.toString()
        )

        // When: 이벤트 핸들러 호출
        contentEventHandler.handleContentCreated(event)

        // 비동기 처리를 위한 대기 (실제 환경에서는 @Async가 동작하지만, 테스트에서는 동기 실행)
        Thread.sleep(100)

        // Then: ContentInteraction이 생성되었는지 확인
        val savedRecord = dslContext.selectFrom(CONTENT_INTERACTIONS)
            .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
            .fetchOne()

        assertNotNull(savedRecord, "ContentInteraction이 생성되어야 합니다")
        assertEquals(contentId.toString(), savedRecord?.contentId)
        assertEquals(0, savedRecord?.likeCount)
        assertEquals(0, savedRecord?.commentCount)
        assertEquals(0, savedRecord?.saveCount)
        assertEquals(0, savedRecord?.shareCount)
        assertEquals(0, savedRecord?.viewCount)
        assertEquals(creatorId.toString(), savedRecord?.createdBy)
        assertEquals(creatorId.toString(), savedRecord?.updatedBy)
    }

    @Test
    @DisplayName("ContentInteraction의 모든 카운터가 0으로 초기화된다")
    fun handleContentCreated_InitializesCountersToZero() {
        // Given: 콘텐츠 ID와 생성자 ID
        val contentId = UUID.randomUUID()
        val creatorId = UUID.randomUUID()

        val event = ContentCreatedEvent(
            contentId = contentId,
            creatorId = creatorId
        )

        // 콘텐츠 레코드 생성
        dslContext.execute(
            """
            INSERT INTO CONTENTS (ID, CREATOR_ID, CONTENT_TYPE, URL, THUMBNAIL_URL, WIDTH, HEIGHT, STATUS, CREATED_AT, UPDATED_AT)
            VALUES (?, ?, 'PHOTO', 'https://example.com/photo.jpg', 'https://example.com/thumb.jpg', 1080, 1080, 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            contentId.toString(),
            creatorId.toString()
        )

        // When: 이벤트 핸들러 호출
        contentEventHandler.handleContentCreated(event)

        // 비동기 처리 대기
        Thread.sleep(100)

        // Then: 모든 카운터가 0인지 확인
        val savedRecord = dslContext.selectFrom(CONTENT_INTERACTIONS)
            .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
            .fetchOne()

        assertEquals(0, savedRecord?.likeCount)
        assertEquals(0, savedRecord?.saveCount)
        assertEquals(0, savedRecord?.shareCount)
    }
}
