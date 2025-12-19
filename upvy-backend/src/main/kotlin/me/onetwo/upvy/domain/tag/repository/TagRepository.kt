package me.onetwo.upvy.domain.tag.repository

import me.onetwo.upvy.domain.tag.model.Tag
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * 태그 레포지토리 인터페이스 (Reactive)
 *
 * 태그 데이터베이스 CRUD를 담당합니다.
 * R2DBC를 통해 완전한 Non-blocking 처리를 지원합니다.
 */
interface TagRepository {

    /**
     * 태그를 저장합니다.
     *
     * @param tag 저장할 태그
     * @return 저장된 태그 (Auto Increment ID 포함, Mono)
     */
    fun save(tag: Tag): Mono<Tag>

    /**
     * 태그를 ID로 조회합니다.
     *
     * @param tagId 태그 ID
     * @return 조회된 태그 (없으면 empty Mono)
     */
    fun findById(tagId: Long): Mono<Tag>

    /**
     * 태그 이름으로 조회합니다.
     *
     * 태그 중복 체크 및 기존 태그 재사용 시 사용됩니다.
     *
     * @param name 태그 이름 (대소문자 구분)
     * @return 조회된 태그 (없으면 empty Mono)
     */
    fun findByName(name: String): Mono<Tag>

    /**
     * 정규화된 이름으로 태그를 조회합니다.
     *
     * 검색용으로 사용하며, 대소문자 무관 검색을 지원합니다.
     *
     * @param normalizedName 정규화된 태그 이름 (소문자, 공백 제거)
     * @return 조회된 태그 (없으면 empty Mono)
     */
    fun findByNormalizedName(normalizedName: String): Mono<Tag>

    /**
     * 여러 태그 ID로 태그를 배치 조회합니다.
     *
     * @param tagIds 조회할 태그 ID 목록
     * @return 태그 목록 (Flux)
     */
    fun findByIds(tagIds: List<Long>): Flux<Tag>

    /**
     * 인기 태그를 usage_count 기준 내림차순으로 조회합니다.
     *
     * Manticore Search와 동일한 결과를 제공하는 Failover용 메서드입니다.
     *
     * @param limit 최대 조회 개수
     * @return 인기 태그 목록 (usage_count 내림차순, Flux)
     */
    fun findPopularTags(limit: Int): Flux<Tag>

    /**
     * 태그 사용 횟수를 증가시킵니다.
     *
     * 콘텐츠에 태그가 추가될 때 usage_count를 1 증가시킵니다.
     * 원자적(Atomic) 연산으로 동시성 문제를 방지합니다.
     *
     * @param tagId 태그 ID
     * @return 증가 성공 여부 (Mono)
     */
    fun incrementUsageCount(tagId: Long): Mono<Boolean>

    /**
     * 태그 사용 횟수를 감소시킵니다.
     *
     * 콘텐츠에서 태그가 제거될 때 usage_count를 1 감소시킵니다.
     * 원자적(Atomic) 연산으로 동시성 문제를 방지합니다.
     *
     * @param tagId 태그 ID
     * @return 감소 성공 여부 (Mono)
     */
    fun decrementUsageCount(tagId: Long): Mono<Boolean>

    /**
     * 태그를 삭제합니다 (Soft Delete).
     *
     * @param tagId 삭제할 태그 ID
     * @param deletedBy 삭제한 사용자 ID
     * @return 삭제 성공 여부 (Mono)
     */
    fun delete(tagId: Long, deletedBy: String): Mono<Boolean>
}
