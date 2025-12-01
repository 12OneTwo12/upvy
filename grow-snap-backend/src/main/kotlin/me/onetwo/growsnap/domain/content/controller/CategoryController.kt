package me.onetwo.growsnap.domain.content.controller

import me.onetwo.growsnap.domain.content.dto.CategoriesResponse
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * 카테고리 컨트롤러
 *
 * 콘텐츠 카테고리 조회 API를 제공합니다.
 */
@RestController
@RequestMapping(ApiPaths.API_V1_CATEGORIES)
class CategoryController {

    /**
     * 카테고리 목록 조회
     *
     * 시스템에서 지원하는 모든 카테고리 정보를 조회합니다.
     * OTHER 카테고리는 제외됩니다.
     *
     * ### 캐싱 전략
     * - 카테고리 정보는 정적 데이터이므로 캐싱을 적용합니다.
     * - Cache Key: "categories"
     * - TTL: Spring Cache 설정에 따름
     *
     * ### 응답 예시
     * ```json
     * {
     *   "categories": [
     *     {
     *       "name": "LANGUAGE",
     *       "displayName": "언어",
     *       "description": "외국어, 한국어 등"
     *     },
     *     {
     *       "name": "SCIENCE",
     *       "displayName": "과학",
     *       "description": "물리, 화학, 생물 등"
     *     }
     *   ]
     * }
     * ```
     *
     * @return 카테고리 목록 응답 (200 OK)
     */
    @GetMapping
    @Cacheable(value = ["categories"], key = "'all'")
    fun getCategories(): Mono<ResponseEntity<CategoriesResponse>> {
        logger.debug("Fetching all categories")

        val response = CategoriesResponse.from()
        logger.debug("Returning {} categories", response.categories.size)

        return Mono.just(ResponseEntity.ok(response))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CategoryController::class.java)
    }
}
