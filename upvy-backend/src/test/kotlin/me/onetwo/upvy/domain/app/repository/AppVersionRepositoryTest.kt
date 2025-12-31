package me.onetwo.upvy.domain.app.repository

import me.onetwo.upvy.domain.app.exception.AppVersionNotFoundException
import me.onetwo.upvy.domain.app.model.AppVersion
import me.onetwo.upvy.domain.app.model.Platform
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import me.onetwo.upvy.jooq.generated.tables.references.APP_VERSIONS
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

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@DisplayName("앱 버전 Repository 통합 테스트")
class AppVersionRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var appVersionRepository: AppVersionRepository

    /**
     * 각 테스트 전에 app_versions 테이블 데이터를 정리하여 테스트 격리 보장
     * dslContext는 AbstractIntegrationTest에서 상속받아 사용
     */
    @BeforeEach
    fun setUp() {
        Mono.from(dslContext.deleteFrom(APP_VERSIONS)).block()
    }

    @Nested
    @DisplayName("save 테스트")
    inner class SaveTest {

        @Test
        @DisplayName("iOS 앱 버전 설정을 저장하고 ID를 반환한다")
        fun savesIosVersionAndReturnsWithId() {
            // Given
            val appVersion = AppVersion(
                platform = Platform.IOS,
                minimumVersion = "1.0.0",
                latestVersion = "1.4.2",
                storeUrl = "https://apps.apple.com/app/upvy/id123456789",
                forceUpdate = true,
                createdBy = "admin"
            )

            // When
            val result = appVersionRepository.save(appVersion)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { saved ->
                    saved.id != null &&
                        saved.platform == Platform.IOS &&
                        saved.minimumVersion == "1.0.0" &&
                        saved.latestVersion == "1.4.2" &&
                        saved.storeUrl == "https://apps.apple.com/app/upvy/id123456789" &&
                        saved.forceUpdate
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("Android 앱 버전 설정을 저장할 수 있다")
        fun savesAndroidVersion() {
            // Given
            val appVersion = AppVersion(
                platform = Platform.ANDROID,
                minimumVersion = "1.0.0",
                latestVersion = "1.4.2",
                storeUrl = "https://play.google.com/store/apps/details?id=com.upvy.app",
                forceUpdate = true,
                createdBy = "admin"
            )

            // When
            val result = appVersionRepository.save(appVersion)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { saved ->
                    saved.id != null &&
                        saved.platform == Platform.ANDROID &&
                        saved.minimumVersion == "1.0.0" &&
                        saved.latestVersion == "1.4.2"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("강제 업데이트가 비활성화된 버전 설정을 저장할 수 있다")
        fun savesVersionWithForceUpdateDisabled() {
            // Given
            val appVersion = AppVersion(
                platform = Platform.IOS,
                minimumVersion = "1.0.0",
                latestVersion = "1.4.2",
                storeUrl = "https://apps.apple.com/app/upvy/id123456789",
                forceUpdate = false,
                createdBy = "admin"
            )

            // When
            val result = appVersionRepository.save(appVersion)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { saved ->
                    saved.id != null &&
                        !saved.forceUpdate
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findByPlatform 테스트")
    inner class FindByPlatformTest {

        @Test
        @DisplayName("iOS 플랫폼의 버전 설정을 조회한다")
        fun findsIosVersion() {
            // Given
            val appVersion = AppVersion(
                platform = Platform.IOS,
                minimumVersion = "1.0.0",
                latestVersion = "1.4.2",
                storeUrl = "https://apps.apple.com/app/upvy/id123456789",
                forceUpdate = true,
                createdBy = "admin"
            )
            appVersionRepository.save(appVersion).block()

            // When
            val result = appVersionRepository.findByPlatform(Platform.IOS)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { found ->
                    found.platform == Platform.IOS &&
                        found.minimumVersion == "1.0.0" &&
                        found.latestVersion == "1.4.2" &&
                        found.storeUrl == "https://apps.apple.com/app/upvy/id123456789"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("Android 플랫폼의 버전 설정을 조회한다")
        fun findsAndroidVersion() {
            // Given
            val appVersion = AppVersion(
                platform = Platform.ANDROID,
                minimumVersion = "1.0.0",
                latestVersion = "1.4.2",
                storeUrl = "https://play.google.com/store/apps/details?id=com.upvy.app",
                forceUpdate = true,
                createdBy = "admin"
            )
            appVersionRepository.save(appVersion).block()

            // When
            val result = appVersionRepository.findByPlatform(Platform.ANDROID)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { found ->
                    found.platform == Platform.ANDROID &&
                        found.minimumVersion == "1.0.0" &&
                        found.latestVersion == "1.4.2"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("존재하지 않는 플랫폼의 경우 empty를 반환한다")
        fun returnsEmptyForNonExistingPlatform() {
            // When
            val result = appVersionRepository.findByPlatform(Platform.IOS)

            // Then
            StepVerifier.create(result)
                .expectError(AppVersionNotFoundException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("update 테스트")
    inner class UpdateTest {

        @Test
        @DisplayName("최소 버전과 최신 버전을 업데이트한다")
        fun updatesVersions() {
            // Given
            val appVersion = AppVersion(
                platform = Platform.IOS,
                minimumVersion = "1.0.0",
                latestVersion = "1.4.0",
                storeUrl = "https://apps.apple.com/app/upvy/id123456789",
                forceUpdate = true,
                createdBy = "admin"
            )
            val saved = appVersionRepository.save(appVersion).block()!!

            val updatedVersion = saved.copy(
                minimumVersion = "1.2.0",
                latestVersion = "1.4.2",
                updatedBy = "admin"
            )

            // When
            val result = appVersionRepository.update(updatedVersion)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { updated ->
                    updated.minimumVersion == "1.2.0" &&
                        updated.latestVersion == "1.4.2" &&
                        updated.platform == Platform.IOS
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("스토어 URL을 업데이트한다")
        fun updatesStoreUrl() {
            // Given
            val appVersion = AppVersion(
                platform = Platform.IOS,
                minimumVersion = "1.0.0",
                latestVersion = "1.4.2",
                storeUrl = "https://apps.apple.com/app/upvy/id123456789",
                forceUpdate = true,
                createdBy = "admin"
            )
            val saved = appVersionRepository.save(appVersion).block()!!

            val updatedVersion = saved.copy(
                storeUrl = "https://apps.apple.com/app/upvy/id987654321",
                updatedBy = "admin"
            )

            // When
            val result = appVersionRepository.update(updatedVersion)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { updated ->
                    updated.storeUrl == "https://apps.apple.com/app/upvy/id987654321"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("강제 업데이트 설정을 변경한다")
        fun updatesForceUpdateFlag() {
            // Given
            val appVersion = AppVersion(
                platform = Platform.IOS,
                minimumVersion = "1.0.0",
                latestVersion = "1.4.2",
                storeUrl = "https://apps.apple.com/app/upvy/id123456789",
                forceUpdate = true,
                createdBy = "admin"
            )
            val saved = appVersionRepository.save(appVersion).block()!!

            val updatedVersion = saved.copy(
                forceUpdate = false,
                updatedBy = "admin"
            )

            // When
            val result = appVersionRepository.update(updatedVersion)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { updated ->
                    !updated.forceUpdate
                }
                .verifyComplete()
        }
    }
}
