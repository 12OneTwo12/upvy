package me.onetwo.upvy.domain.app.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.upvy.domain.app.exception.AppVersionNotFoundException
import me.onetwo.upvy.domain.app.model.AppVersion
import me.onetwo.upvy.domain.app.model.Platform
import me.onetwo.upvy.domain.app.repository.AppVersionRepository
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

/**
 * AppVersionService 단위 테스트
 */
@DisplayName("앱 버전 Service 테스트")
class AppVersionServiceTest : BaseReactiveTest {

    private lateinit var appVersionRepository: AppVersionRepository
    private lateinit var appVersionService: AppVersionService

    @BeforeEach
    fun setUp() {
        appVersionRepository = mockk()
        appVersionService = AppVersionServiceImpl(appVersionRepository)
    }

    private fun createTestAppVersion(
        platform: Platform = Platform.IOS,
        minimumVersion: String = "1.0.0",
        latestVersion: String = "1.4.2",
        forceUpdate: Boolean = true
    ): AppVersion {
        return AppVersion(
            id = 1L,
            platform = platform,
            minimumVersion = minimumVersion,
            latestVersion = latestVersion,
            storeUrl = "https://apps.apple.com/app/upvy/id123456789",
            forceUpdate = forceUpdate,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    @Nested
    @DisplayName("checkVersion 테스트")
    inner class CheckVersionTest {

        @Test
        @DisplayName("현재 버전이 최소 버전보다 낮으면 업데이트가 필요하다")
        fun needsUpdateWhenCurrentVersionBelowMinimum() {
            // Given
            val appVersion = createTestAppVersion(
                minimumVersion = "1.2.0",
                latestVersion = "1.4.2"
            )
            every { appVersionRepository.findByPlatform(Platform.IOS) } returns Mono.just(appVersion)

            // When
            val result = appVersionService.checkVersion(Platform.IOS, "1.0.0")

            // Then
            StepVerifier.create(result)
                .expectNextMatches { response ->
                    response.needsUpdate &&
                        !response.isLatestVersion &&
                        response.latestVersion == "1.4.2" &&
                        response.minimumVersion == "1.2.0" &&
                        response.storeUrl != null
                }
                .verifyComplete()

            verify(exactly = 1) { appVersionRepository.findByPlatform(Platform.IOS) }
        }

        @Test
        @DisplayName("현재 버전이 최소 버전 이상이고 최신 버전보다 낮으면 업데이트는 불필요하지만 최신 버전은 아니다")
        fun noUpdateNeededButNotLatest() {
            // Given
            val appVersion = createTestAppVersion(
                minimumVersion = "1.0.0",
                latestVersion = "1.4.2"
            )
            every { appVersionRepository.findByPlatform(Platform.IOS) } returns Mono.just(appVersion)

            // When
            val result = appVersionService.checkVersion(Platform.IOS, "1.2.0")

            // Then
            StepVerifier.create(result)
                .expectNextMatches { response ->
                    !response.needsUpdate &&
                        !response.isLatestVersion &&
                        response.latestVersion == "1.4.2" &&
                        response.minimumVersion == "1.0.0" &&
                        response.storeUrl == null  // 업데이트 불필요하면 storeUrl은 null
                }
                .verifyComplete()

            verify(exactly = 1) { appVersionRepository.findByPlatform(Platform.IOS) }
        }

        @Test
        @DisplayName("현재 버전이 최신 버전과 같으면 업데이트가 불필요하고 최신 버전이다")
        fun isLatestVersionWhenSameAsLatest() {
            // Given
            val appVersion = createTestAppVersion(
                minimumVersion = "1.0.0",
                latestVersion = "1.4.2"
            )
            every { appVersionRepository.findByPlatform(Platform.IOS) } returns Mono.just(appVersion)

            // When
            val result = appVersionService.checkVersion(Platform.IOS, "1.4.2")

            // Then
            StepVerifier.create(result)
                .expectNextMatches { response ->
                    !response.needsUpdate &&
                        response.isLatestVersion &&
                        response.latestVersion == "1.4.2" &&
                        response.storeUrl == null
                }
                .verifyComplete()

            verify(exactly = 1) { appVersionRepository.findByPlatform(Platform.IOS) }
        }

        @Test
        @DisplayName("현재 버전이 최신 버전보다 높으면 업데이트가 불필요하고 최신 버전으로 간주한다")
        fun isLatestVersionWhenHigherThanLatest() {
            // Given
            val appVersion = createTestAppVersion(
                minimumVersion = "1.0.0",
                latestVersion = "1.4.2"
            )
            every { appVersionRepository.findByPlatform(Platform.IOS) } returns Mono.just(appVersion)

            // When
            val result = appVersionService.checkVersion(Platform.IOS, "1.5.0")

            // Then
            StepVerifier.create(result)
                .expectNextMatches { response ->
                    !response.needsUpdate &&
                        response.isLatestVersion &&
                        response.storeUrl == null
                }
                .verifyComplete()

            verify(exactly = 1) { appVersionRepository.findByPlatform(Platform.IOS) }
        }

        @Test
        @DisplayName("forceUpdate가 false이면 최소 버전보다 낮아도 업데이트가 불필요하다")
        fun noUpdateNeededWhenForceUpdateDisabled() {
            // Given
            val appVersion = createTestAppVersion(
                minimumVersion = "1.2.0",
                latestVersion = "1.4.2",
                forceUpdate = false
            )
            every { appVersionRepository.findByPlatform(Platform.IOS) } returns Mono.just(appVersion)

            // When
            val result = appVersionService.checkVersion(Platform.IOS, "1.0.0")

            // Then
            StepVerifier.create(result)
                .expectNextMatches { response ->
                    !response.needsUpdate &&
                        !response.isLatestVersion &&
                        response.storeUrl == null
                }
                .verifyComplete()

            verify(exactly = 1) { appVersionRepository.findByPlatform(Platform.IOS) }
        }

        @Test
        @DisplayName("Android 플랫폼도 동일하게 동작한다")
        fun worksForAndroidPlatform() {
            // Given
            val appVersion = createTestAppVersion(
                platform = Platform.ANDROID,
                minimumVersion = "1.0.0",
                latestVersion = "1.4.2"
            )
            every { appVersionRepository.findByPlatform(Platform.ANDROID) } returns Mono.just(appVersion)

            // When
            val result = appVersionService.checkVersion(Platform.ANDROID, "1.4.2")

            // Then
            StepVerifier.create(result)
                .expectNextMatches { response ->
                    !response.needsUpdate &&
                        response.isLatestVersion &&
                        response.latestVersion == "1.4.2"
                }
                .verifyComplete()

            verify(exactly = 1) { appVersionRepository.findByPlatform(Platform.ANDROID) }
        }

        @Test
        @DisplayName("플랫폼에 대한 버전 설정이 없으면 AppVersionNotFoundException을 던진다")
        fun throwsExceptionWhenNoConfigFound() {
            // Given
            every { appVersionRepository.findByPlatform(Platform.IOS) } returns Mono.empty()

            // When
            val result = appVersionService.checkVersion(Platform.IOS, "1.0.0")

            // Then
            StepVerifier.create(result)
                .expectError(AppVersionNotFoundException::class.java)
                .verify()

            verify(exactly = 1) { appVersionRepository.findByPlatform(Platform.IOS) }
        }
    }
}
