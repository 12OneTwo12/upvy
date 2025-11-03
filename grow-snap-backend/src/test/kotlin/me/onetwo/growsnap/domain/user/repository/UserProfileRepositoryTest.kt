package me.onetwo.growsnap.domain.user.repository

import java.util.UUID

import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.domain.user.model.UserRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * UserProfileRepository 통합 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("사용자 프로필 Repository 테스트")
class UserProfileRepositoryTest {

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var testUser: User
    private lateinit var testProfile: UserProfile

    @BeforeEach
    fun setUp() {
        // 테스트 사용자 생성
        testUser = userRepository.save(User(
                email = "profile-test@example.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "profile-google-12345",
                role = UserRole.USER
            )).block()!!

        // 테스트 프로필
        testProfile = UserProfile(
            userId = testUser.id!!,
            nickname = "testnick",
            profileImageUrl = "https://example.com/profile.jpg",
            bio = "테스트 자기소개"
        )
    }

    @Test
    @DisplayName("프로필 저장 성공")
    fun save_Success() {
        // When
        val savedProfile = userProfileRepository.save(testProfile).block()!!

        // Then
        assertNotNull(savedProfile.id)
        assertEquals(testProfile.userId, savedProfile.userId)
        assertEquals(testProfile.nickname, savedProfile.nickname)
        assertEquals(testProfile.profileImageUrl, savedProfile.profileImageUrl)
        assertEquals(testProfile.bio, savedProfile.bio)
        assertEquals(0, savedProfile.followerCount)
        assertEquals(0, savedProfile.followingCount)
    }

    @Test
    @DisplayName("사용자 ID로 프로필 조회 성공")
    fun findByUserId_ExistingProfile_ReturnsProfile() {
        // Given
        val savedProfile = userProfileRepository.save(testProfile).block()!!

        // When
        val foundProfile = userProfileRepository.findByUserId(testUser.id!!).block()

        // Then
        assertNotNull(foundProfile)
        assertEquals(savedProfile.id, foundProfile?.id)
        assertEquals(savedProfile.nickname, foundProfile?.nickname)
    }

    @Test
    @DisplayName("사용자 ID로 프로필 조회 - 존재하지 않는 경우")
    fun findByUserId_NonExistingProfile_ReturnsNull() {
        // When
        val foundProfile = userProfileRepository.findByUserId(UUID.randomUUID()).block()

        // Then
        assertNull(foundProfile)
    }

    @Test
    @DisplayName("닉네임으로 프로필 조회 성공")
    fun findByNickname_ExistingProfile_ReturnsProfile() {
        // Given
        val savedProfile = userProfileRepository.save(testProfile).block()!!

        // When
        val foundProfile = userProfileRepository.findByNickname(testProfile.nickname).block()

        // Then
        assertNotNull(foundProfile)
        assertEquals(savedProfile.id, foundProfile?.id)
        assertEquals(savedProfile.nickname, foundProfile?.nickname)
    }

    @Test
    @DisplayName("닉네임으로 프로필 조회 - 존재하지 않는 경우")
    fun findByNickname_NonExistingProfile_ReturnsNull() {
        // When
        val foundProfile = userProfileRepository.findByNickname("nonexistent-nickname").block()

        // Then
        assertNull(foundProfile)
    }

    @Test
    @DisplayName("프로필 업데이트 성공 - 닉네임과 자기소개 변경")
    fun update_ChangeNicknameAndBio_Success() {
        // Given
        val savedProfile = userProfileRepository.save(testProfile).block()!!
        val updatedProfile = savedProfile.copy(
            nickname = "newnick",
            bio = "새로운 자기소개"
        )

        // When
        val result = userProfileRepository.update(updatedProfile).block()!!

        // Then
        assertEquals("newnick", result.nickname)
        assertEquals("새로운 자기소개", result.bio)

        // 실제 DB에서 확인
        val foundProfile = userProfileRepository.findByUserId(testUser.id!!).block()
        assertEquals("newnick", foundProfile?.nickname)
        assertEquals("새로운 자기소개", foundProfile?.bio)
    }

    @Test
    @DisplayName("프로필 업데이트 성공 - 팔로워 수 증가")
    fun update_IncreaseFollowerCount_Success() {
        // Given
        val savedProfile = userProfileRepository.save(testProfile).block()!!
        val updatedProfile = savedProfile.copy(followerCount = 10)

        // When
        val result = userProfileRepository.update(updatedProfile).block()!!

        // Then
        assertEquals(10, result.followerCount)

        // 실제 DB에서 확인
        val foundProfile = userProfileRepository.findByUserId(testUser.id!!).block()
        assertEquals(10, foundProfile?.followerCount)
    }

    @Test
    @DisplayName("닉네임 존재 여부 확인 - 존재하는 경우")
    fun existsByNickname_ExistingNickname_ReturnsTrue() {
        // Given
        userProfileRepository.save(testProfile).block()!!

        // When
        val exists = userProfileRepository.existsByNickname(testProfile.nickname).block()!!

        // Then
        assertTrue(exists)
    }

    @Test
    @DisplayName("닉네임 존재 여부 확인 - 존재하지 않는 경우")
    fun existsByNickname_NonExistingNickname_ReturnsFalse() {
        // When
        val exists = userProfileRepository.existsByNickname("nonexistent-nickname").block()!!

        // Then
        assertFalse(exists)
    }

    @Test
    @DisplayName("중복 닉네임으로 저장 시도 - 예외 발생")
    fun save_DuplicateNickname_ThrowsException() {
        // Given
        userProfileRepository.save(testProfile).block()!!

        // When & Then
        val anotherUser = userRepository.save(User(
                email = "another@example.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "another-google-12345",
                role = UserRole.USER
            )).block()!!
        val duplicateProfile = UserProfile(
            userId = anotherUser.id!!,
            nickname = testProfile.nickname, // 중복 닉네임
            bio = "다른 사용자"
        )

        assertThrows(Exception::class.java) {
            userProfileRepository.save(duplicateProfile).block()!!
        }
    }

    @Test
    @DisplayName("프로필 이미지 URL 업데이트 성공")
    fun update_ChangeProfileImageUrl_Success() {
        // Given
        val savedProfile = userProfileRepository.save(testProfile).block()!!
        val newImageUrl = "https://example.com/new-profile.jpg"
        val updatedProfile = savedProfile.copy(profileImageUrl = newImageUrl)

        // When
        val result = userProfileRepository.update(updatedProfile).block()!!

        // Then
        assertEquals(newImageUrl, result.profileImageUrl)

        // 실제 DB에서 확인
        val foundProfile = userProfileRepository.findByUserId(testUser.id!!).block()
        assertEquals(newImageUrl, foundProfile?.profileImageUrl)
    }
}
