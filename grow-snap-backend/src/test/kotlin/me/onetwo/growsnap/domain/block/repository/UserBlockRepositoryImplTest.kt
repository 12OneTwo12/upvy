package me.onetwo.growsnap.domain.block.repository
import me.onetwo.growsnap.infrastructure.config.AbstractIntegrationTest

import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.jooq.generated.tables.UserBlocks.Companion.USER_BLOCKS
import me.onetwo.growsnap.jooq.generated.tables.UserProfiles.Companion.USER_PROFILES
import me.onetwo.growsnap.jooq.generated.tables.Users.Companion.USERS
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * 사용자 차단 Repository 통합 테스트
 *
 * UserBlockRepositoryImpl의 데이터베이스 CRUD 동작을 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("사용자 차단 Repository 통합 테스트")
class UserBlockRepositoryImplTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var userBlockRepository: UserBlockRepository

    

    private lateinit var blockerId: UUID
    private lateinit var blockedId: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트용 사용자 생성
        val uniqueSuffix = UUID.randomUUID().toString().substring(0, 8)
        blockerId = insertUser("blocker-$uniqueSuffix@test.com", "google-blocker-$uniqueSuffix")
        blockedId = insertUser("blocked-$uniqueSuffix@test.com", "google-blocked-$uniqueSuffix")
    }

    @AfterEach
    fun tearDown() {
        // Given: 테스트 데이터 정리

        // When: 모든 테스트 데이터 삭제
        Mono.from(dslContext.deleteFrom(USER_BLOCKS)).block()
        Mono.from(dslContext.deleteFrom(USER_PROFILES)).block()
        Mono.from(dslContext.deleteFrom(USERS)).block()
    }

    @Nested
    @DisplayName("save - 사용자 차단 생성")
    inner class Save {

        @Test
        @DisplayName("사용자 차단을 생성한다")
        fun save_CreatesUserBlock() {
            // Given: 차단할 사용자 정보

            // When: 사용자 차단 생성
            val result = userBlockRepository.save(blockerId, blockedId)

            // Then: 차단이 성공적으로 생성됨
            StepVerifier.create(result)
                .assertNext { userBlock ->
                    assertThat(userBlock.id).isNotNull()
                    assertThat(userBlock.blockerId).isEqualTo(blockerId)
                    assertThat(userBlock.blockedId).isEqualTo(blockedId)
                    assertThat(userBlock.createdAt).isNotNull()
                    assertThat(userBlock.createdBy).isEqualTo(blockerId.toString())
                    assertThat(userBlock.updatedAt).isNotNull()
                    assertThat(userBlock.updatedBy).isEqualTo(blockerId.toString())
                    assertThat(userBlock.deletedAt).isNull()
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("Audit Trail이 자동으로 설정된다")
        fun save_SetsAuditTrailAutomatically() {
            // Given: 차단할 사용자 정보

            // When: 사용자 차단 생성
            val result = userBlockRepository.save(blockerId, blockedId)

            // Then: Audit Trail 필드가 자동으로 설정됨
            StepVerifier.create(result)
                .assertNext { userBlock ->
                    assertThat(userBlock.createdAt).isNotNull()
                    assertThat(userBlock.createdBy).isEqualTo(blockerId.toString())
                    assertThat(userBlock.updatedAt).isNotNull()
                    assertThat(userBlock.updatedBy).isEqualTo(blockerId.toString())
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("삭제된 차단을 다시 차단하면 복원된다 (UPSERT)")
        fun save_WhenBlockIsDeleted_ResurrectBlock() {
            // Given: 사용자 차단 생성 후 삭제
            userBlockRepository.save(blockerId, blockedId).block()!!
            userBlockRepository.delete(blockerId, blockedId).block()

            // When: 삭제된 차단을 다시 차단
            val result = userBlockRepository.save(blockerId, blockedId)

            // Then: 차단이 복원됨 (deleted_at = NULL)
            StepVerifier.create(result)
                .assertNext { userBlock ->
                    assertThat(userBlock.blockerId).isEqualTo(blockerId)
                    assertThat(userBlock.blockedId).isEqualTo(blockedId)
                    assertThat(userBlock.deletedAt).isNull()  // 복원됨
                }
                .verifyComplete()

            // Then: exists()로 확인해도 존재함
            val exists = userBlockRepository.exists(blockerId, blockedId).block()
            assertThat(exists).isTrue()
        }

        @Test
        @DisplayName("이미 차단한 사용자를 다시 차단해도 성공한다 (Idempotent)")
        fun save_WhenAlreadyBlocked_IsIdempotent() {
            // Given: 이미 차단한 사용자
            userBlockRepository.save(blockerId, blockedId).block()

            // When: 동일한 사용자를 다시 차단 시도
            val result = userBlockRepository.save(blockerId, blockedId)

            // Then: 성공적으로 완료됨 (중복 에러 없음)
            StepVerifier.create(result)
                .assertNext { userBlock ->
                    assertThat(userBlock.blockerId).isEqualTo(blockerId)
                    assertThat(userBlock.blockedId).isEqualTo(blockedId)
                    assertThat(userBlock.deletedAt).isNull()
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("exists - 사용자 차단 존재 여부 확인")
    inner class Exists {

        @Test
        @DisplayName("차단이 존재하면 true를 반환한다")
        fun exists_WhenBlockExists_ReturnsTrue() {
            // Given: 사용자 차단 생성
            userBlockRepository.save(blockerId, blockedId).block()

            // When: 차단 존재 여부 확인
            val result = userBlockRepository.exists(blockerId, blockedId)

            // Then: true 반환
            StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete()
        }

        @Test
        @DisplayName("차단이 존재하지 않으면 false를 반환한다")
        fun exists_WhenBlockDoesNotExist_ReturnsFalse() {
            // Given: 차단이 없는 상태

            // When: 차단 존재 여부 확인
            val result = userBlockRepository.exists(blockerId, blockedId)

            // Then: false 반환
            StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete()
        }

        @Test
        @DisplayName("삭제된 차단은 존재하지 않는 것으로 간주한다")
        fun exists_WhenBlockIsDeleted_ReturnsFalse() {
            // Given: 사용자 차단 생성 후 삭제
            userBlockRepository.save(blockerId, blockedId).block()
            userBlockRepository.delete(blockerId, blockedId).block()

            // When: 차단 존재 여부 확인
            val result = userBlockRepository.exists(blockerId, blockedId)

            // Then: false 반환 (Soft Delete)
            StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findByBlockerIdAndBlockedId - 사용자 차단 조회")
    inner class FindByBlockerIdAndBlockedId {

        @Test
        @DisplayName("차단이 존재하면 UserBlock을 반환한다")
        fun findByBlockerIdAndBlockedId_WhenBlockExists_ReturnsUserBlock() {
            // Given: 사용자 차단 생성
            val saved = userBlockRepository.save(blockerId, blockedId).block()!!

            // When: 차단 조회
            val result = userBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)

            // Then: UserBlock 반환
            StepVerifier.create(result)
                .assertNext { userBlock ->
                    assertThat(userBlock.id).isEqualTo(saved.id)
                    assertThat(userBlock.blockerId).isEqualTo(blockerId)
                    assertThat(userBlock.blockedId).isEqualTo(blockedId)
                    assertThat(userBlock.deletedAt).isNull()
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("차단이 존재하지 않으면 empty를 반환한다")
        fun findByBlockerIdAndBlockedId_WhenBlockDoesNotExist_ReturnsEmpty() {
            // Given: 차단이 없는 상태

            // When: 차단 조회
            val result = userBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)

            // Then: empty 반환
            StepVerifier.create(result)
                .verifyComplete()
        }

        @Test
        @DisplayName("삭제된 차단도 조회된다 (UPSERT 지원)")
        fun findByBlockerIdAndBlockedId_WhenBlockIsDeleted_ReturnsDeletedBlock() {
            // Given: 사용자 차단 생성 후 삭제
            userBlockRepository.save(blockerId, blockedId).block()
            userBlockRepository.delete(blockerId, blockedId).block()

            // When: 차단 조회
            val result = userBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)

            // Then: 삭제된 차단 반환 (UPSERT 패턴 지원)
            StepVerifier.create(result)
                .assertNext { userBlock ->
                    assertThat(userBlock.blockerId).isEqualTo(blockerId)
                    assertThat(userBlock.blockedId).isEqualTo(blockedId)
                    assertThat(userBlock.deletedAt).isNotNull()  // 삭제된 상태
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("delete - 사용자 차단 삭제 (Soft Delete)")
    inner class Delete {

        @Test
        @DisplayName("차단을 소프트 삭제한다")
        fun delete_SoftDeletesBlock() {
            // Given: 사용자 차단 생성
            val saved = userBlockRepository.save(blockerId, blockedId).block()!!

            // When: 차단 삭제
            val result = userBlockRepository.delete(blockerId, blockedId)

            // Then: 삭제 성공
            StepVerifier.create(result)
                .verifyComplete()

            // Then: deleted_at이 설정됨 (Soft Delete 확인)
            val deletedBlock = Mono.from(
                dslContext
                    .select(USER_BLOCKS.DELETED_AT)
                    .from(USER_BLOCKS)
                    .where(USER_BLOCKS.ID.eq(saved.id))
            ).block()

            assertThat(deletedBlock).isNotNull()
            assertThat(deletedBlock!!.getValue(USER_BLOCKS.DELETED_AT)).isNotNull()
        }

        @Test
        @DisplayName("존재하지 않는 차단 삭제 시 성공적으로 완료된다")
        fun delete_WhenBlockDoesNotExist_CompletesSuccessfully() {
            // Given: 차단이 없는 상태

            // When: 차단 삭제 시도
            val result = userBlockRepository.delete(blockerId, blockedId)

            // Then: 에러 없이 완료
            StepVerifier.create(result)
                .verifyComplete()
        }

        @Test
        @DisplayName("삭제 시 updated_at과 updated_by가 업데이트된다")
        fun delete_UpdatesAuditTrail() {
            // Given: 사용자 차단 생성
            val saved = userBlockRepository.save(blockerId, blockedId).block()!!
            val originalUpdatedAt = saved.updatedAt

            // When: 차단 삭제
            userBlockRepository.delete(blockerId, blockedId).block()

            // Then: updated_at과 updated_by가 업데이트됨
            val deletedBlock = Mono.from(
                dslContext
                    .select(USER_BLOCKS.UPDATED_AT, USER_BLOCKS.UPDATED_BY, USER_BLOCKS.DELETED_AT)
                    .from(USER_BLOCKS)
                    .where(USER_BLOCKS.ID.eq(saved.id))
            ).block()

            assertThat(deletedBlock).isNotNull()
            assertThat(deletedBlock!!.getValue(USER_BLOCKS.UPDATED_AT)).isAfter(originalUpdatedAt)
            assertThat(deletedBlock.getValue(USER_BLOCKS.UPDATED_BY)).isEqualTo(blockerId.toString())
            assertThat(deletedBlock.getValue(USER_BLOCKS.DELETED_AT)).isNotNull()
        }
    }

    @Nested
    @DisplayName("findBlockedUsersByBlockerId - 차단한 사용자 목록 조회")
    inner class FindBlockedUsersByBlockerId {

        @Test
        @DisplayName("차단한 사용자 목록을 반환한다")
        fun findBlockedUsersByBlockerId_ReturnsBlockedUsers() {
            // Given: 여러 사용자 차단
            val uniqueSuffix = UUID.randomUUID().toString().substring(0, 8)
            val blocked1 = insertUser("blocked1-$uniqueSuffix@test.com", "google-blocked1-$uniqueSuffix")
            val blocked2 = insertUser("blocked2-$uniqueSuffix@test.com", "google-blocked2-$uniqueSuffix")
            val blocked3 = insertUser("blocked3-$uniqueSuffix@test.com", "google-blocked3-$uniqueSuffix")

            insertUserProfile(blocked1, "Blocked User 1", null)
            insertUserProfile(blocked2, "Blocked User 2", null)
            insertUserProfile(blocked3, "Blocked User 3", null)

            userBlockRepository.save(blockerId, blocked1).block()
            userBlockRepository.save(blockerId, blocked2).block()
            userBlockRepository.save(blockerId, blocked3).block()

            // When: 차단한 사용자 목록 조회
            val result = userBlockRepository.findBlockedUsersByBlockerId(blockerId, null, 10)

            // Then: 3개의 차단된 사용자 반환 (최신순)
            StepVerifier.create(result.collectList())
                .assertNext { items ->
                    assertThat(items).hasSize(3)
                    assertThat(items[0].userId).isEqualTo(blocked3.toString())
                    assertThat(items[0].nickname).isEqualTo("Blocked User 3")
                    assertThat(items[1].userId).isEqualTo(blocked2.toString())
                    assertThat(items[1].nickname).isEqualTo("Blocked User 2")
                    assertThat(items[2].userId).isEqualTo(blocked1.toString())
                    assertThat(items[2].nickname).isEqualTo("Blocked User 1")
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("커서 기반 페이지네이션이 동작한다")
        fun findBlockedUsersByBlockerId_WithCursor_ReturnsPaginatedResults() {
            // Given: 여러 사용자 차단
            val uniqueSuffix = UUID.randomUUID().toString().substring(0, 8)
            val blocked1 = insertUser("blocked1-$uniqueSuffix@test.com", "google-blocked1-$uniqueSuffix")
            val blocked2 = insertUser("blocked2-$uniqueSuffix@test.com", "google-blocked2-$uniqueSuffix")
            val blocked3 = insertUser("blocked3-$uniqueSuffix@test.com", "google-blocked3-$uniqueSuffix")

            insertUserProfile(blocked1, "Blocked User 1", null)
            insertUserProfile(blocked2, "Blocked User 2", null)
            insertUserProfile(blocked3, "Blocked User 3", null)

            userBlockRepository.save(blockerId, blocked1).block()
            val block2 = userBlockRepository.save(blockerId, blocked2).block()!!
            userBlockRepository.save(blockerId, blocked3).block()

            // When: 커서로 두 번째 차단 이후 조회
            val result = userBlockRepository.findBlockedUsersByBlockerId(blockerId, block2.id, 10)

            // Then: 첫 번째 차단만 반환
            StepVerifier.create(result.collectList())
                .assertNext { items ->
                    assertThat(items).hasSize(1)
                    assertThat(items[0].userId).isEqualTo(blocked1.toString())
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("limit이 적용된다")
        fun findBlockedUsersByBlockerId_WithLimit_ReturnsLimitedResults() {
            // Given: 여러 사용자 차단
            val uniqueSuffix = UUID.randomUUID().toString().substring(0, 8)
            val blocked1 = insertUser("blocked1-$uniqueSuffix@test.com", "google-blocked1-$uniqueSuffix")
            val blocked2 = insertUser("blocked2-$uniqueSuffix@test.com", "google-blocked2-$uniqueSuffix")
            val blocked3 = insertUser("blocked3-$uniqueSuffix@test.com", "google-blocked3-$uniqueSuffix")

            insertUserProfile(blocked1, "Blocked User 1", null)
            insertUserProfile(blocked2, "Blocked User 2", null)
            insertUserProfile(blocked3, "Blocked User 3", null)

            userBlockRepository.save(blockerId, blocked1).block()
            userBlockRepository.save(blockerId, blocked2).block()
            userBlockRepository.save(blockerId, blocked3).block()

            // When: limit 2로 조회
            val result = userBlockRepository.findBlockedUsersByBlockerId(blockerId, null, 2)

            // Then: 2개만 반환
            StepVerifier.create(result.collectList())
                .assertNext { items ->
                    assertThat(items).hasSize(2)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("삭제된 차단은 조회되지 않는다")
        fun findBlockedUsersByBlockerId_ExcludesDeletedBlocks() {
            // Given: 여러 사용자 차단 후 하나 삭제
            val uniqueSuffix = UUID.randomUUID().toString().substring(0, 8)
            val blocked1 = insertUser("blocked1-$uniqueSuffix@test.com", "google-blocked1-$uniqueSuffix")
            val blocked2 = insertUser("blocked2-$uniqueSuffix@test.com", "google-blocked2-$uniqueSuffix")

            insertUserProfile(blocked1, "Blocked User 1", null)
            insertUserProfile(blocked2, "Blocked User 2", null)

            userBlockRepository.save(blockerId, blocked1).block()
            userBlockRepository.save(blockerId, blocked2).block()
            userBlockRepository.delete(blockerId, blocked2).block()

            // When: 차단한 사용자 목록 조회
            val result = userBlockRepository.findBlockedUsersByBlockerId(blockerId, null, 10)

            // Then: 삭제되지 않은 차단만 반환
            StepVerifier.create(result.collectList())
                .assertNext { items ->
                    assertThat(items).hasSize(1)
                    assertThat(items[0].userId).isEqualTo(blocked1.toString())
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("차단한 사용자가 없으면 빈 목록을 반환한다")
        fun findBlockedUsersByBlockerId_WhenNoBlocks_ReturnsEmptyList() {
            // Given: 차단이 없는 상태

            // When: 차단한 사용자 목록 조회
            val result = userBlockRepository.findBlockedUsersByBlockerId(blockerId, null, 10)

            // Then: 빈 목록 반환
            StepVerifier.create(result.collectList())
                .assertNext { items ->
                    assertThat(items).isEmpty()
                }
                .verifyComplete()
        }
    }

    /**
     * 테스트용 사용자 생성 헬퍼 메서드
     */
    private fun insertUser(email: String, providerId: String): UUID {
        val userId = UUID.randomUUID()
        val now = Instant.now()

        Mono.from(
            dslContext.insertInto(USERS)
                .set(USERS.ID, userId.toString())
                .set(USERS.EMAIL, email)
                .set(USERS.PROVIDER, OAuthProvider.GOOGLE.name)
                .set(USERS.PROVIDER_ID, providerId)
                .set(USERS.ROLE, UserRole.USER.name)
                .set(USERS.CREATED_AT, now)
                .set(USERS.CREATED_BY, userId.toString())
                .set(USERS.UPDATED_AT, now)
                .set(USERS.UPDATED_BY, userId.toString())
        ).block()

        return userId
    }

    /**
     * 테스트용 사용자 프로필 생성 헬퍼 메서드
     */
    private fun insertUserProfile(userId: UUID, nickname: String, profileImageUrl: String?) {
        val now = Instant.now()

        Mono.from(
            dslContext.insertInto(USER_PROFILES)
                .set(USER_PROFILES.USER_ID, userId.toString())
                .set(USER_PROFILES.NICKNAME, nickname)
                .set(USER_PROFILES.PROFILE_IMAGE_URL, profileImageUrl)
                .set(USER_PROFILES.CREATED_AT, now)
                .set(USER_PROFILES.CREATED_BY, userId.toString())
                .set(USER_PROFILES.UPDATED_AT, now)
                .set(USER_PROFILES.UPDATED_BY, userId.toString())
        ).block()
    }
}
