package me.onetwo.upvy.infrastructure.manticore

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Manticore Search 설정 프로퍼티
 *
 * application.yml에서 manticore.search 설정을 바인딩합니다.
 *
 * ## 설정 예시 (application.yml)
 * ```yaml
 * manticore:
 *   search:
 *     base-url: http://localhost:9308
 *     timeout: 5000
 *     index:
 *       content: content_index
 *       autocomplete: autocomplete_index
 * ```
 *
 * @property baseUrl Manticore Search HTTP API URL (기본값: http://localhost:9308)
 * @property timeout 요청 타임아웃 (밀리초, 기본값: 5000)
 * @property index 인덱스 이름 설정
 */
@ConfigurationProperties(prefix = "manticore.search")
data class ManticoreSearchProperties(
    val baseUrl: String = "http://localhost:9308",
    val timeout: Long = 5000,
    val index: IndexProperties = IndexProperties()
)

/**
 * Manticore Search 인덱스 이름 설정
 *
 * @property content 콘텐츠 검색 인덱스 이름 (기본값: content_index)
 * @property user 사용자 검색 인덱스 이름 (기본값: user_index)
 * @property autocomplete 자동완성 인덱스 이름 (기본값: autocomplete_index)
 */
data class IndexProperties(
    val content: String = "content_index",
    val user: String = "user_index",
    val autocomplete: String = "autocomplete_index"
)
