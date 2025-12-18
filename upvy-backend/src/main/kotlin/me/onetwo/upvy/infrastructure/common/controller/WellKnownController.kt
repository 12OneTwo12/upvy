package me.onetwo.upvy.infrastructure.common.controller

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.net.URI

/**
 * Apple App Site Association (AASA) 파일 구조
 */
data class AppleAppSiteAssociation(
    val applinks: AppLinks,
    val webcredentials: WebCredentials
) {
    data class AppLinks(
        val apps: List<String> = emptyList(),
        val details: List<AppLinkDetail>
    )

    data class AppLinkDetail(
        val appID: String,
        val paths: List<String>
    )

    data class WebCredentials(
        val apps: List<String>
    )
}

/**
 * Android Asset Links 파일 구조
 */
data class AndroidAssetLink(
    val relation: List<String>,
    val target: Target
) {
    data class Target(
        val namespace: String,
        val package_name: String,
        val sha256_cert_fingerprints: List<String>
    )
}

@RestController
class WellKnownController(
    @Value("\${upvy.universal-links.apple-team-id:PLACEHOLDER_TEAM_ID}")
    private val appleTeamId: String,

    @Value("\${upvy.universal-links.android-sha256-fingerprint:PLACEHOLDER_FINGERPRINT}")
    private val androidSha256Fingerprint: String
) {

    @GetMapping(
        path = ["/.well-known/apple-app-site-association"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun getAppleAppSiteAssociation(): Mono<AppleAppSiteAssociation> {
        logger.debug("Serving apple-app-site-association for Universal Links")

        val appId = "$appleTeamId.com.upvy.app"

        val aasa = AppleAppSiteAssociation(
            applinks = AppleAppSiteAssociation.AppLinks(
                details = listOf(
                    AppleAppSiteAssociation.AppLinkDetail(
                        appID = appId,
                        paths = listOf("/watch/*")
                    )
                )
            ),
            webcredentials = AppleAppSiteAssociation.WebCredentials(
                apps = listOf(appId)
            )
        )

        return Mono.just(aasa)
    }

    @GetMapping(
        path = ["/.well-known/assetlinks.json"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun getAssetLinks(): Mono<List<AndroidAssetLink>> {
        logger.debug("Serving assetlinks.json for Android App Links")

        val assetLink = AndroidAssetLink(
            relation = listOf("delegate_permission/common.handle_all_urls"),
            target = AndroidAssetLink.Target(
                namespace = "android_app",
                package_name = "com.upvy.app",
                sha256_cert_fingerprints = listOf(androidSha256Fingerprint)
            )
        )

        return Mono.just(listOf(assetLink))
    }

    @GetMapping("/watch/{contentId}")
    fun handleWatchRedirect(
        @PathVariable contentId: String,
        request: ServerHttpRequest
    ): Mono<ResponseEntity<Void>> {
        val userAgent = request.headers.getFirst("User-Agent") ?: ""

        logger.debug("Handling /watch redirect: contentId={}, userAgent={}", contentId, userAgent)

        val redirectUrl = when {
            userAgent.contains("iPhone", ignoreCase = true) ||
            userAgent.contains("iPad", ignoreCase = true) ||
            userAgent.contains("iPod", ignoreCase = true) -> {
                logger.debug("Redirecting iOS device to App Store")
                APP_STORE_URL
            }
            userAgent.contains("Android", ignoreCase = true) -> {
                logger.debug("Redirecting Android device to Play Store")
                PLAY_STORE_URL
            }
            else -> {
                logger.debug("Redirecting desktop/unknown device to Docs homepage")
                DOCS_HOMEPAGE_URL
            }
        }

        return Mono.just(
            ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build()
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WellKnownController::class.java)

        private const val APP_STORE_URL = "https://apps.apple.com/app/upvy/id6756291696"
        private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.upvy.app"
        private const val DOCS_HOMEPAGE_URL = "https://upvy.org"
    }
}
