package me.onetwo.upvy.infrastructure.common

/**
 * API 경로 상수
 *
 * 모든 컨트롤러에서 사용하는 API 경로를 관리합니다.
 * 일관성 유지 및 변경 시 한 곳에서만 수정하도록 합니다.
 */
object ApiPaths {
    const val API_V1 = "/api/v1"
    const val API_V1_AUTH = "$API_V1/auth"
    const val API_V1_USERS = "$API_V1/users"
    const val API_V1_PROFILES = "$API_V1/profiles"
    const val API_V1_FOLLOWS = "$API_V1/follows"
    const val API_V1_CONTENTS = "$API_V1/contents"
    const val API_V1_FEED = "$API_V1/feed"
    const val API_V1_ANALYTICS = "$API_V1/analytics"
    const val API_V1_SEARCH = "$API_V1/search"
    const val API_V1_CATEGORIES = "$API_V1/categories"
    const val API_V1_NOTIFICATIONS = "$API_V1/notifications"
    const val API_V1_PUSH_TOKENS = "$API_V1/push-tokens"
    const val API_V1_TAGS = "$API_V1/tags"
    const val API_V1_APP_VERSION = "$API_V1/app-version"
}
