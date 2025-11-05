package me.onetwo.growsnap.domain.search.event

import me.onetwo.growsnap.domain.search.model.SearchType
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.infrastructure.event.ReactiveEventPublisher
import me.onetwo.growsnap.jooq.generated.tables.references.SEARCH_HISTORY
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * SearchEventSubscriber 통합 테스트
 *
 * Reactor Sinks API를 사용하여 SearchEventSubscriber가 제대로 동작하는지 검증합니다.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@DisplayName("검색 이벤트 Subscriber 통합 테스트")
class SearchEventSubscriberTest {

    @Autowired
    private lateinit var eventPublisher: ReactiveEventPublisher

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        // Given: 테스트 사용자 생성
        testUser = User(
            id = UUID.randomUUID(),
            email = "test@example.com",
            provider = OAuthProvider.GOOGLE,
            providerId = "google-12345",
            role = UserRole.USER
        )
        userRepository.save(testUser).block()

        val testProfile = UserProfile(
            userId = testUser.id!!,
            nickname = "테스트유저",
            profileImageUrl = null,
            bio = null
        )
        userProfileRepository.save(testProfile).block()
    }

    @AfterEach
    fun tearDown() {
        // 테스트 데이터 정리 (Reactive)
        Mono.from(dslContext.deleteFrom(SEARCH_HISTORY)).block()
    }

    @Test
    @DisplayName("SearchPerformedEvent 발행 시, 검색 기록이 비동기로 저장된다")
    fun handleSearchPerformed_WithEvent_SavesSearchHistory() {
        // Given: 검색 수행 이벤트
        val event = SearchPerformedEvent(
            userId = testUser.id,
            keyword = "프로그래밍",
            searchType = SearchType.CONTENT
        )

        // When: 이벤트 발행 (Reactor Sinks API)
        eventPublisher.publish(event)

        // Then: Awaitility로 비동기 처리 대기
        await.atMost(2, TimeUnit.SECONDS).untilAsserted {
            val count = Mono.from(
                dslContext.selectCount()
                    .from(SEARCH_HISTORY)
                    .where(SEARCH_HISTORY.USER_ID.eq(testUser.id.toString()))
                    .and(SEARCH_HISTORY.KEYWORD.eq("프로그래밍"))
                    .and(SEARCH_HISTORY.SEARCH_TYPE.eq(SearchType.CONTENT.name))
                    .and(SEARCH_HISTORY.DELETED_AT.isNull)
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .block()
            assertThat(count).isEqualTo(1)
        }
    }

    @Test
    @DisplayName("userId가 null인 이벤트는 검색 기록을 저장하지 않는다")
    fun handleSearchPerformed_WithNullUserId_DoesNotSaveSearchHistory() {
        // Given: userId가 null인 이벤트 (비인증 사용자 검색)
        val event = SearchPerformedEvent(
            userId = null,
            keyword = "프로그래밍",
            searchType = SearchType.CONTENT
        )

        // When: 이벤트 발행 (Reactor Sinks API)
        eventPublisher.publish(event)

        // Then: Awaitility로 비동기 처리 대기 (pollDelay 추가: 즉시 처리되지 않음을 확인)
        await.pollDelay(500, TimeUnit.MILLISECONDS).atMost(1, TimeUnit.SECONDS).untilAsserted {
            // Then: 검색 기록이 저장되지 않음
            val count = Mono.from(
                dslContext.selectCount()
                    .from(SEARCH_HISTORY)
                    .where(SEARCH_HISTORY.KEYWORD.eq("프로그래밍"))
                    .and(SEARCH_HISTORY.DELETED_AT.isNull)
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .block()
            assertThat(count).isEqualTo(0)
        }
    }

    @Test
    @DisplayName("사용자 검색 이벤트도 검색 기록에 저장된다")
    fun handleSearchPerformed_WithUserSearch_SavesSearchHistory() {
        // Given: 사용자 검색 이벤트
        val event = SearchPerformedEvent(
            userId = testUser.id,
            keyword = "홍길동",
            searchType = SearchType.USER
        )

        // When: 이벤트 발행 (Reactor Sinks API)
        eventPublisher.publish(event)

        // Then: Awaitility로 비동기 처리 대기
        await.atMost(2, TimeUnit.SECONDS).untilAsserted {
            val count = Mono.from(
                dslContext.selectCount()
                    .from(SEARCH_HISTORY)
                    .where(SEARCH_HISTORY.USER_ID.eq(testUser.id.toString()))
                    .and(SEARCH_HISTORY.KEYWORD.eq("홍길동"))
                    .and(SEARCH_HISTORY.SEARCH_TYPE.eq(SearchType.USER.name))
                    .and(SEARCH_HISTORY.DELETED_AT.isNull)
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .block()
            assertThat(count).isEqualTo(1)
        }
    }

    @Test
    @DisplayName("동일한 키워드로 여러 번 검색하면, 각각 저장된다")
    fun handleSearchPerformed_WithSameKeyword_SavesMultipleTimes() {
        // Given: 동일한 키워드로 3번 검색 이벤트 발행
        val event = SearchPerformedEvent(
            userId = testUser.id,
            keyword = "Kotlin",
            searchType = SearchType.CONTENT
        )

        // When: 3번 이벤트 발행 (Reactor Sinks API)
        eventPublisher.publish(event)
        eventPublisher.publish(event)
        eventPublisher.publish(event)

        // Then: Awaitility로 비동기 처리 대기 - 3개의 검색 기록이 저장됨
        await.atMost(2, TimeUnit.SECONDS).untilAsserted {
            val count = Mono.from(
                dslContext.selectCount()
                    .from(SEARCH_HISTORY)
                    .where(SEARCH_HISTORY.USER_ID.eq(testUser.id.toString()))
                    .and(SEARCH_HISTORY.KEYWORD.eq("Kotlin"))
                    .and(SEARCH_HISTORY.DELETED_AT.isNull)
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .block()
            assertThat(count).isEqualTo(3)
        }
    }
}
