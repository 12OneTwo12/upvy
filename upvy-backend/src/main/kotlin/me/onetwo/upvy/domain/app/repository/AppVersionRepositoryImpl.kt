package me.onetwo.upvy.domain.app.repository

import me.onetwo.upvy.domain.app.model.AppVersion
import me.onetwo.upvy.domain.app.model.Platform
import me.onetwo.upvy.jooq.generated.tables.references.APP_VERSIONS
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * 앱 버전 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class AppVersionRepositoryImpl(
    private val dslContext: DSLContext
) : AppVersionRepository {

    private val logger = LoggerFactory.getLogger(AppVersionRepositoryImpl::class.java)

    override fun findByPlatform(platform: Platform): Mono<AppVersion> {
        return Mono.from(
            dslContext
                .select(
                    APP_VERSIONS.ID,
                    APP_VERSIONS.PLATFORM,
                    APP_VERSIONS.MINIMUM_VERSION,
                    APP_VERSIONS.LATEST_VERSION,
                    APP_VERSIONS.STORE_URL,
                    APP_VERSIONS.FORCE_UPDATE,
                    APP_VERSIONS.CREATED_AT,
                    APP_VERSIONS.CREATED_BY,
                    APP_VERSIONS.UPDATED_AT,
                    APP_VERSIONS.UPDATED_BY,
                    APP_VERSIONS.DELETED_AT
                )
                .from(APP_VERSIONS)
                .where(APP_VERSIONS.PLATFORM.eq(platform.name))
                .and(APP_VERSIONS.DELETED_AT.isNull)
        ).map { record ->
            AppVersion(
                id = record.getValue(APP_VERSIONS.ID),
                platform = Platform.valueOf(record.getValue(APP_VERSIONS.PLATFORM)!!),
                minimumVersion = record.getValue(APP_VERSIONS.MINIMUM_VERSION)!!,
                latestVersion = record.getValue(APP_VERSIONS.LATEST_VERSION)!!,
                storeUrl = record.getValue(APP_VERSIONS.STORE_URL)!!,
                forceUpdate = record.getValue(APP_VERSIONS.FORCE_UPDATE) ?: true,
                createdAt = record.getValue(APP_VERSIONS.CREATED_AT) ?: Instant.now(),
                createdBy = record.getValue(APP_VERSIONS.CREATED_BY),
                updatedAt = record.getValue(APP_VERSIONS.UPDATED_AT) ?: Instant.now(),
                updatedBy = record.getValue(APP_VERSIONS.UPDATED_BY),
                deletedAt = record.getValue(APP_VERSIONS.DELETED_AT)
            )
        }.doOnNext { appVersion ->
            logger.debug("AppVersion found: platform=${appVersion.platform}, minimumVersion=${appVersion.minimumVersion}, latestVersion=${appVersion.latestVersion}")
        }
    }

    override fun save(appVersion: AppVersion): Mono<AppVersion> {
        return Mono.from(
            dslContext
                .insertInto(APP_VERSIONS)
                .set(APP_VERSIONS.PLATFORM, appVersion.platform.name)
                .set(APP_VERSIONS.MINIMUM_VERSION, appVersion.minimumVersion)
                .set(APP_VERSIONS.LATEST_VERSION, appVersion.latestVersion)
                .set(APP_VERSIONS.STORE_URL, appVersion.storeUrl)
                .set(APP_VERSIONS.FORCE_UPDATE, appVersion.forceUpdate)
                .set(APP_VERSIONS.CREATED_AT, appVersion.createdAt)
                .set(APP_VERSIONS.CREATED_BY, appVersion.createdBy)
                .set(APP_VERSIONS.UPDATED_AT, appVersion.updatedAt)
                .set(APP_VERSIONS.UPDATED_BY, appVersion.updatedBy)
                .returningResult(APP_VERSIONS.ID)
        ).map { record ->
            val generatedId = record.getValue(APP_VERSIONS.ID)
            logger.info("AppVersion saved: id=$generatedId, platform=${appVersion.platform}")
            appVersion.copy(id = generatedId)
        }
    }

    override fun update(appVersion: AppVersion): Mono<AppVersion> {
        val now = Instant.now()
        return Mono.from(
            dslContext
                .update(APP_VERSIONS)
                .set(APP_VERSIONS.MINIMUM_VERSION, appVersion.minimumVersion)
                .set(APP_VERSIONS.LATEST_VERSION, appVersion.latestVersion)
                .set(APP_VERSIONS.STORE_URL, appVersion.storeUrl)
                .set(APP_VERSIONS.FORCE_UPDATE, appVersion.forceUpdate)
                .set(APP_VERSIONS.UPDATED_AT, now)
                .set(APP_VERSIONS.UPDATED_BY, appVersion.updatedBy)
                .where(APP_VERSIONS.ID.eq(appVersion.id))
                .and(APP_VERSIONS.DELETED_AT.isNull)
        ).then(findByPlatform(appVersion.platform))
            .doOnNext { updatedAppVersion ->
                logger.info("AppVersion updated: platform=${updatedAppVersion.platform}, minimumVersion=${updatedAppVersion.minimumVersion}, latestVersion=${updatedAppVersion.latestVersion}")
            }
    }
}
