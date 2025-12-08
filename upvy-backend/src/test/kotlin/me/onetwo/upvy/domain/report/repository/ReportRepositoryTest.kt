package me.onetwo.upvy.domain.report.repository
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest

import me.onetwo.upvy.domain.content.model.ContentStatus
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.domain.report.model.ReportType
import me.onetwo.upvy.domain.report.model.TargetType
import me.onetwo.upvy.domain.user.model.OAuthProvider
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.jooq.generated.tables.ContentInteractions.Companion.CONTENT_INTERACTIONS
import me.onetwo.upvy.jooq.generated.tables.ContentMetadata.Companion.CONTENT_METADATA
import me.onetwo.upvy.jooq.generated.tables.Contents.Companion.CONTENTS
import me.onetwo.upvy.jooq.generated.tables.Reports.Companion.REPORTS
import org.jooq.DSLContext
import org.jooq.JSON
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * ReportRepository 통합 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ReportRepository 통합 테스트")
class ReportRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var reportRepository: ReportRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    

    private lateinit var testUserId: UUID
    private lateinit var testContentId: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트 데이터 준비
        val user = userRepository.save(
            me.onetwo.upvy.domain.user.model.User(
                email = "testuser@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "test-provider-id",
                role = UserRole.USER
            )
        ).block()!!
        testUserId = user.id!!

        // 콘텐츠 생성
        testContentId = UUID.randomUUID()
        insertContent(testContentId, testUserId, "Test Content")
    }

    @AfterEach
    fun tearDown() {
        Mono.from(dslContext.deleteFrom(REPORTS)).block()
        Mono.from(dslContext.deleteFrom(CONTENTS)).block()
        Mono.from(dslContext.deleteFrom(me.onetwo.upvy.jooq.generated.tables.UserProfiles.Companion.USER_PROFILES)).block()
        Mono.from(dslContext.deleteFrom(me.onetwo.upvy.jooq.generated.tables.Users.Companion.USERS)).block()
    }

    @Nested
    @DisplayName("save - 신고 생성")
    inner class Save {

        @Test
        @DisplayName("신고를 생성하면, reports 테이블에 레코드가 저장된다")
        fun save_CreatesReport() {
            // Given: 준비된 사용자와 콘텐츠

            // When: 신고 생성
            val report = reportRepository.save(
                reporterId = testUserId,
                targetType = TargetType.CONTENT,
                targetId = testContentId,
                reportType = ReportType.SPAM,
                description = "스팸 콘텐츠입니다"
            ).block()!!

            // Then: 생성된 신고 검증
            assertEquals(testUserId, report.reporterId)
            assertEquals(TargetType.CONTENT, report.targetType)
            assertEquals(testContentId, report.targetId)
            assertEquals(ReportType.SPAM, report.reportType)
            assertEquals("스팸 콘텐츠입니다", report.description)
        }
    }

    @Nested
    @DisplayName("exists - 신고 존재 여부 확인")
    inner class Exists {

        @Test
        @DisplayName("신고가 존재하면, true를 반환한다")
        fun exists_WhenExists_ReturnsTrue() {
            // Given: 신고가 존재
            reportRepository.save(
                testUserId,
                TargetType.CONTENT,
                testContentId,
                ReportType.SPAM,
                null
            ).block()

            // When: 존재 여부 확인
            val exists = reportRepository.exists(testUserId, TargetType.CONTENT, testContentId).block()!!

            // Then: true 반환
            assertTrue(exists)
        }

        @Test
        @DisplayName("신고가 존재하지 않으면, false를 반환한다")
        fun exists_WhenNotExists_ReturnsFalse() {
            // Given: 신고가 존재하지 않음

            // When: 존재 여부 확인
            val exists = reportRepository.exists(testUserId, TargetType.CONTENT, testContentId).block()!!

            // Then: false 반환
            assertFalse(exists)
        }
    }

    /**
     * 콘텐츠 삽입 헬퍼 메서드
     */
    private fun insertContent(
        contentId: UUID,
        creatorId: UUID,
        title: String
    ) {
        val now = Instant.now()

        Mono.from(dslContext.insertInto(CONTENTS)
            .set(CONTENTS.ID, contentId.toString())
            .set(CONTENTS.CREATOR_ID, creatorId.toString())
            .set(CONTENTS.CONTENT_TYPE, ContentType.VIDEO.name)
            .set(CONTENTS.URL, "https://example.com/$contentId.mp4")
            .set(CONTENTS.THUMBNAIL_URL, "https://example.com/$contentId-thumb.jpg")
            .set(CONTENTS.DURATION, 60)
            .set(CONTENTS.WIDTH, 1920)
            .set(CONTENTS.HEIGHT, 1080)
            .set(CONTENTS.STATUS, ContentStatus.PUBLISHED.name)
            .set(CONTENTS.CREATED_AT, now)
            .set(CONTENTS.CREATED_BY, creatorId.toString())
            .set(CONTENTS.UPDATED_AT, now)
            .set(CONTENTS.UPDATED_BY, creatorId.toString())).block()

        Mono.from(dslContext.insertInto(CONTENT_METADATA)
            .set(CONTENT_METADATA.CONTENT_ID, contentId.toString())
            .set(CONTENT_METADATA.TITLE, title)
            .set(CONTENT_METADATA.DESCRIPTION, "Test Description")
            .set(CONTENT_METADATA.CATEGORY, "TEST")
            .set(CONTENT_METADATA.TAGS, JSON.valueOf("[\"test\"]"))
            .set(CONTENT_METADATA.LANGUAGE, "ko")
            .set(CONTENT_METADATA.CREATED_AT, now)
            .set(CONTENT_METADATA.CREATED_BY, creatorId.toString())
            .set(CONTENT_METADATA.UPDATED_AT, now)
            .set(CONTENT_METADATA.UPDATED_BY, creatorId.toString())).block()

        Mono.from(dslContext.insertInto(CONTENT_INTERACTIONS)
            .set(CONTENT_INTERACTIONS.CONTENT_ID, contentId.toString())
            .set(CONTENT_INTERACTIONS.VIEW_COUNT, 0)
            .set(CONTENT_INTERACTIONS.LIKE_COUNT, 0)
            .set(CONTENT_INTERACTIONS.COMMENT_COUNT, 0)
            .set(CONTENT_INTERACTIONS.SAVE_COUNT, 0)
            .set(CONTENT_INTERACTIONS.SHARE_COUNT, 0)
            .set(CONTENT_INTERACTIONS.CREATED_AT, now)
            .set(CONTENT_INTERACTIONS.CREATED_BY, creatorId.toString())
            .set(CONTENT_INTERACTIONS.UPDATED_AT, now)
            .set(CONTENT_INTERACTIONS.UPDATED_BY, creatorId.toString())).block()
    }
}
