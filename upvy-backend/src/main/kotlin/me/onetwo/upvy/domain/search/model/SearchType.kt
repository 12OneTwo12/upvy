package me.onetwo.upvy.domain.search.model

/**
 * 검색 타입
 *
 * 사용자가 어떤 종류의 검색을 수행했는지 구분합니다.
 *
 * @property CONTENT 콘텐츠 검색
 * @property USER 사용자 검색
 */
enum class SearchType {
    /**
     * 콘텐츠 검색
     */
    CONTENT,

    /**
     * 사용자 검색
     */
    USER
}
