package me.onetwo.growsnap.domain.search.repository

import me.onetwo.growsnap.domain.search.model.SearchType
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.jooq.generated.tables.references.SEARCH_HISTORY
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime
import java.util.UUID

/**
 * SearchHistoryRepository 통합 테스트
 *
 * 실제 데이터베이스(H2)를 사용하여 검색 기록 기능을 검증합니다.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@DisplayName("검색 기록 Repository 통합 테스트")
class SearchHistoryRepositoryImplTest {

    @Autowired
    private lateinit var searchHistoryRepository: SearchHistoryRepository

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
            email = "search-history-repo-test@example.com",
            provider = OAuthProvider.GOOGLE,
            providerId = "google-search-history-repo-12345",
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
        // 테스트 데이터 정리
        Mono.from(dslContext.deleteFrom(SEARCH_HISTORY)).block()
    }

    @Nested
    @DisplayName("save - 검색 기록 저장")
    inner class Save {

        @Test
        @DisplayName("사용자가 콘텐츠를 검색하면, 검색 기록이 저장된다")
        fun save_ContentSearch_SavesSearchHistory() {
            // Given: 검색 키워드
            val keyword = "프로그래밍"
            val searchType = SearchType.CONTENT

            // When: 검색 기록 저장
            val result = searchHistoryRepository.save(testUser.id!!, keyword, searchType)

            // Then: 검색 기록이 저장되었는지 확인
            StepVerifier.create(result)
                .assertNext { history ->
                    assertThat(history.id).isNotNull()
                    assertThat(history.userId).isEqualTo(testUser.id!!)
                    assertThat(history.keyword).isEqualTo(keyword)
                    assertThat(history.searchType).isEqualTo(searchType)
                    assertThat(history.createdAt).isNotNull()
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("사용자가 사용자를 검색하면, 검색 기록이 저장된다")
        fun save_UserSearch_SavesSearchHistory() {
            // Given: 검색 키워드
            val keyword = "홍길동"
            val searchType = SearchType.USER

            // When: 검색 기록 저장
            val result = searchHistoryRepository.save(testUser.id!!, keyword, searchType)

            // Then: 검색 기록이 저장되었는지 확인
            StepVerifier.create(result)
                .assertNext { history ->
                    assertThat(history.userId).isEqualTo(testUser.id!!)
                    assertThat(history.keyword).isEqualTo(keyword)
                    assertThat(history.searchType).isEqualTo(searchType)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("동일한 키워드로 여러 번 검색하면, 중복 저장된다")
        fun save_DuplicateKeyword_SavesMultipleTimes() {
            // Given: 동일한 키워드
            val keyword = "프로그래밍"
            val searchType = SearchType.CONTENT

            // When: 동일한 키워드로 3번 검색
            searchHistoryRepository.save(testUser.id!!, keyword, searchType).block()
            searchHistoryRepository.save(testUser.id!!, keyword, searchType).block()
            searchHistoryRepository.save(testUser.id!!, keyword, searchType).block()

            // Then: 3개의 검색 기록이 저장됨
            val count = Mono.from(
                dslContext.selectCount()
                    .from(SEARCH_HISTORY)
                    .where(SEARCH_HISTORY.USER_ID.eq(testUser.id.toString()))
                    .and(SEARCH_HISTORY.KEYWORD.eq(keyword))
                    .and(SEARCH_HISTORY.DELETED_AT.isNull)
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .block()
            assertThat(count).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("findRecentByUserId - 최근 검색어 조회")
    inner class FindRecentByUserId {

        @Test
        @DisplayName("사용자의 최근 검색어를 최신순으로 조회한다")
        fun findRecentByUserId_WithSearchHistory_ReturnsRecentSearches() {
            // Given: 3개의 검색 기록 (명시적 시간 설정)
            insertSearchHistory(testUser.id!!, "Java", SearchType.CONTENT, LocalDateTime.now().minusHours(3))
            insertSearchHistory(testUser.id!!, "Kotlin", SearchType.CONTENT, LocalDateTime.now().minusHours(2))
            insertSearchHistory(testUser.id!!, "Python", SearchType.CONTENT, LocalDateTime.now().minusHours(1))

            // When: 최근 검색어 조회
            val result = searchHistoryRepository.findRecentByUserId(testUser.id!!, 10)

            // Then: 최신순으로 정렬되어 반환됨
            StepVerifier.create(result)
                .assertNext { histories ->
                    assertThat(histories).hasSize(3)
                    assertThat(histories[0].keyword).isEqualTo("Python")
                    assertThat(histories[1].keyword).isEqualTo("Kotlin")
                    assertThat(histories[2].keyword).isEqualTo("Java")
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("동일한 키워드는 가장 최근 검색만 반환한다 (중복 제거)")
        fun findRecentByUserId_WithDuplicateKeyword_ReturnsOnlyLatest() {
            // Given: 동일한 키워드로 여러 번 검색 (명시적 시간 설정)
            insertSearchHistory(testUser.id!!, "Java", SearchType.CONTENT, LocalDateTime.now().minusHours(3))
            insertSearchHistory(testUser.id!!, "Kotlin", SearchType.CONTENT, LocalDateTime.now().minusHours(2))
            insertSearchHistory(testUser.id!!, "Java", SearchType.CONTENT, LocalDateTime.now().minusHours(1)) // 중복

            // When: 최근 검색어 조회
            val result = searchHistoryRepository.findRecentByUserId(testUser.id!!, 10)

            // Then: Java는 1개만 반환됨 (가장 최근 것)
            StepVerifier.create(result)
                .assertNext { histories ->
                    assertThat(histories).hasSize(2)
                    assertThat(histories[0].keyword).isEqualTo("Java")
                    assertThat(histories[1].keyword).isEqualTo("Kotlin")
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("limit 개수만큼만 반환한다")
        fun findRecentByUserId_WithLimit_ReturnsLimitedResults() {
            // Given: 5개의 검색 기록 (명시적 시간 설정)
            val now = LocalDateTime.now()
            repeat(5) { i ->
                insertSearchHistory(testUser.id!!, "Keyword$i", SearchType.CONTENT, now.minusHours((5 - i).toLong()))
            }

            // When: limit 3으로 조회
            val result = searchHistoryRepository.findRecentByUserId(testUser.id!!, 3)

            // Then: 3개만 반환됨
            StepVerifier.create(result)
                .assertNext { histories ->
                    assertThat(histories).hasSize(3)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("검색 기록이 없으면, 빈 리스트를 반환한다")
        fun findRecentByUserId_WithNoHistory_ReturnsEmptyList() {
            // When: 검색 기록 조회
            val result = searchHistoryRepository.findRecentByUserId(testUser.id!!, 10)

            // Then: 빈 리스트 반환
            StepVerifier.create(result)
                .assertNext { histories ->
                    assertThat(histories).isEmpty()
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("다른 사용자의 검색 기록은 조회되지 않는다")
        fun findRecentByUserId_WithOtherUserHistory_ReturnsOnlyCurrentUserHistory() {
            // Given: 다른 사용자 생성
            val otherUser = User(
                id = UUID.randomUUID(),
                email = "other@example.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "google-67890",
                role = UserRole.USER
            )
            userRepository.save(otherUser).block()

            val otherProfile = UserProfile(
                userId = otherUser.id!!,
                nickname = "다른유저",
                profileImageUrl = null,
                bio = null
            )
            userProfileRepository.save(otherProfile).block()

            // Given: 각 사용자의 검색 기록
            searchHistoryRepository.save(testUser.id!!, "Java", SearchType.CONTENT).block()
            searchHistoryRepository.save(otherUser.id!!, "Python", SearchType.CONTENT).block()

            // When: testUser의 검색 기록 조회
            val result = searchHistoryRepository.findRecentByUserId(testUser.id!!, 10)

            // Then: testUser의 검색 기록만 반환됨
            StepVerifier.create(result)
                .assertNext { histories ->
                    assertThat(histories).hasSize(1)
                    assertThat(histories[0].keyword).isEqualTo("Java")
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("deleteByUserIdAndKeyword - 특정 검색어 삭제")
    inner class DeleteByUserIdAndKeyword {

        @Test
        @DisplayName("특정 검색어를 삭제하면, 해당 키워드의 모든 검색 기록이 소프트 삭제된다")
        fun deleteByUserIdAndKeyword_DeletesSearchHistory() {
            // Given: 검색 기록
            searchHistoryRepository.save(testUser.id!!, "Java", SearchType.CONTENT).block()
            searchHistoryRepository.save(testUser.id!!, "Java", SearchType.CONTENT).block()
            searchHistoryRepository.save(testUser.id!!, "Kotlin", SearchType.CONTENT).block()

            // When: "Java" 검색어 삭제
            val result = searchHistoryRepository.deleteByUserIdAndKeyword(testUser.id!!, "Java")

            // Then: 삭제 완료
            StepVerifier.create(result)
                .verifyComplete()

            // Then: "Java" 검색 기록이 소프트 삭제됨
            val javaCount = Mono.from(
                dslContext.selectCount()
                    .from(SEARCH_HISTORY)
                    .where(SEARCH_HISTORY.USER_ID.eq(testUser.id.toString()))
                    .and(SEARCH_HISTORY.KEYWORD.eq("Java"))
                    .and(SEARCH_HISTORY.DELETED_AT.isNull)
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .block()
            assertThat(javaCount).isEqualTo(0)

            // Then: "Kotlin" 검색 기록은 유지됨
            val kotlinCount = Mono.from(
                dslContext.selectCount()
                    .from(SEARCH_HISTORY)
                    .where(SEARCH_HISTORY.USER_ID.eq(testUser.id.toString()))
                    .and(SEARCH_HISTORY.KEYWORD.eq("Kotlin"))
                    .and(SEARCH_HISTORY.DELETED_AT.isNull)
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .block()
            assertThat(kotlinCount).isEqualTo(1)
        }

        @Test
        @DisplayName("존재하지 않는 키워드를 삭제하면, 아무 일도 일어나지 않는다")
        fun deleteByUserIdAndKeyword_WithNonExistentKeyword_DoesNothing() {
            // Given: 검색 기록
            searchHistoryRepository.save(testUser.id!!, "Java", SearchType.CONTENT).block()

            // When: 존재하지 않는 키워드 삭제
            val result = searchHistoryRepository.deleteByUserIdAndKeyword(testUser.id!!, "Python")

            // Then: 삭제 완료 (오류 없음)
            StepVerifier.create(result)
                .verifyComplete()

            // Then: 기존 검색 기록은 유지됨
            val count = Mono.from(
                dslContext.selectCount()
                    .from(SEARCH_HISTORY)
                    .where(SEARCH_HISTORY.USER_ID.eq(testUser.id.toString()))
                    .and(SEARCH_HISTORY.DELETED_AT.isNull)
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .block()
            assertThat(count).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("deleteAllByUserId - 전체 검색 기록 삭제")
    inner class DeleteAllByUserId {

        @Test
        @DisplayName("사용자의 모든 검색 기록을 소프트 삭제한다")
        fun deleteAllByUserId_DeletesAllSearchHistory() {
            // Given: 여러 검색 기록
            searchHistoryRepository.save(testUser.id!!, "Java", SearchType.CONTENT).block()
            searchHistoryRepository.save(testUser.id!!, "Kotlin", SearchType.CONTENT).block()
            searchHistoryRepository.save(testUser.id!!, "홍길동", SearchType.USER).block()

            // When: 전체 검색 기록 삭제
            val result = searchHistoryRepository.deleteAllByUserId(testUser.id!!)

            // Then: 삭제 완료
            StepVerifier.create(result)
                .verifyComplete()

            // Then: 모든 검색 기록이 소프트 삭제됨
            val count = Mono.from(
                dslContext.selectCount()
                    .from(SEARCH_HISTORY)
                    .where(SEARCH_HISTORY.USER_ID.eq(testUser.id.toString()))
                    .and(SEARCH_HISTORY.DELETED_AT.isNull)
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .block()
            assertThat(count).isEqualTo(0)
        }

        @Test
        @DisplayName("검색 기록이 없으면, 아무 일도 일어나지 않는다")
        fun deleteAllByUserId_WithNoHistory_DoesNothing() {
            // When: 전체 검색 기록 삭제
            val result = searchHistoryRepository.deleteAllByUserId(testUser.id!!)

            // Then: 삭제 완료 (오류 없음)
            StepVerifier.create(result)
                .verifyComplete()
        }

        @Test
        @DisplayName("다른 사용자의 검색 기록은 삭제되지 않는다")
        fun deleteAllByUserId_DoesNotDeleteOtherUserHistory() {
            // Given: 다른 사용자 생성
            val otherUser = User(
                id = UUID.randomUUID(),
                email = "other@example.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "google-67890",
                role = UserRole.USER
            )
            userRepository.save(otherUser).block()

            val otherProfile = UserProfile(
                userId = otherUser.id!!,
                nickname = "다른유저",
                profileImageUrl = null,
                bio = null
            )
            userProfileRepository.save(otherProfile).block()

            // Given: 각 사용자의 검색 기록
            searchHistoryRepository.save(testUser.id!!, "Java", SearchType.CONTENT).block()
            searchHistoryRepository.save(otherUser.id!!, "Python", SearchType.CONTENT).block()

            // When: testUser의 검색 기록 삭제
            searchHistoryRepository.deleteAllByUserId(testUser.id!!).block()

            // Then: testUser의 검색 기록만 삭제됨
            val testUserCount = Mono.from(
                dslContext.selectCount()
                    .from(SEARCH_HISTORY)
                    .where(SEARCH_HISTORY.USER_ID.eq(testUser.id.toString()))
                    .and(SEARCH_HISTORY.DELETED_AT.isNull)
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .block()
            assertThat(testUserCount).isEqualTo(0)

            // Then: otherUser의 검색 기록은 유지됨
            val otherUserCount = Mono.from(
                dslContext.selectCount()
                    .from(SEARCH_HISTORY)
                    .where(SEARCH_HISTORY.USER_ID.eq(otherUser.id.toString()))
                    .and(SEARCH_HISTORY.DELETED_AT.isNull)
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .block()
            assertThat(otherUserCount).isEqualTo(1)
        }
    }

    /**
     * 검색 기록 삽입 헬퍼 메서드
     *
     * Thread.sleep() 대신 명시적 시간 설정을 사용합니다.
     */
    private fun insertSearchHistory(
        userId: UUID,
        keyword: String,
        searchType: SearchType,
        createdAt: LocalDateTime
    ) {
        val userIdStr = userId.toString()
        Mono.from(
            dslContext.insertInto(SEARCH_HISTORY)
                .set(SEARCH_HISTORY.USER_ID, userIdStr)
                .set(SEARCH_HISTORY.KEYWORD, keyword)
                .set(SEARCH_HISTORY.SEARCH_TYPE, searchType.name)
                .set(SEARCH_HISTORY.CREATED_AT, createdAt)
                .set(SEARCH_HISTORY.CREATED_BY, userIdStr)
                .set(SEARCH_HISTORY.UPDATED_AT, createdAt)
                .set(SEARCH_HISTORY.UPDATED_BY, userIdStr)
                .returningResult(SEARCH_HISTORY.ID)
        ).block()
    }
}
