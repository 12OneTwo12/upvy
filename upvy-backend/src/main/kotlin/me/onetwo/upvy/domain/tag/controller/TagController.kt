package me.onetwo.upvy.domain.tag.controller

import me.onetwo.upvy.domain.tag.dto.PopularTagsResponse
import me.onetwo.upvy.domain.tag.dto.TagResponse
import me.onetwo.upvy.domain.tag.service.TagService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * 태그 Controller
 *
 * 태그 관련 API를 제공합니다.
 *
 * ## API 엔드포인트
 * - GET /api/v1/tags/popular - 인기 태그 조회
 *
 * @property tagService 태그 Service
 */
@RestController
@RequestMapping(ApiPaths.API_V1_TAGS)
class TagController(
    private val tagService: TagService
) {

    /**
     * 인기 태그 조회
     *
     * 사용 횟수(usage_count)가 높은 순으로 태그를 반환합니다.
     *
     * @param limit 최대 조회 개수 (기본값: 20)
     * @return 인기 태그 목록
     */
    @GetMapping("/popular")
    fun getPopularTags(
        @RequestParam(defaultValue = "20") limit: Int
    ): Mono<ResponseEntity<PopularTagsResponse>> {
        logger.debug("Getting popular tags: limit={}", limit)

        return tagService.getPopularTags(limit)
            .map { tag ->
                TagResponse(
                    id = tag.id!!,
                    name = tag.name,
                    usageCount = tag.usageCount
                )
            }
            .collectList()
            .map { tags ->
                PopularTagsResponse(
                    tags = tags,
                    count = tags.size
                )
            }
            .map { ResponseEntity.ok(it) }
            .doOnSuccess {
                logger.debug("Popular tags retrieved: count={}", it.body?.count)
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TagController::class.java)
    }
}
