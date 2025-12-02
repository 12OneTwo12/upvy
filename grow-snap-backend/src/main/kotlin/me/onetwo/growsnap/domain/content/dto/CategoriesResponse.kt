package me.onetwo.growsnap.domain.content.dto

import me.onetwo.growsnap.domain.content.model.Category

/**
 * 카테고리 목록 응답 DTO
 *
 * 시스템에서 지원하는 모든 카테고리 정보를 제공합니다.
 * OTHER 카테고리는 제외됩니다.
 *
 * @property categories 카테고리 정보 목록
 */
data class CategoriesResponse(
    val categories: List<CategoryInfo>
) {
    /**
     * 카테고리 정보
     *
     * @property name 카테고리 코드 (enum name)
     * @property displayName 화면 표시용 이름
     * @property description 카테고리 설명
     */
    data class CategoryInfo(
        val name: String,
        val displayName: String,
        val description: String
    )

    companion object {
        /**
         * Category enum으로부터 CategoriesResponse 생성
         *
         * OTHER 카테고리는 제외하고 응답을 생성합니다.
         *
         * @return CategoriesResponse
         */
        fun from(): CategoriesResponse {
            val categories = Category.entries
                .filter { it != Category.OTHER }
                .map { category ->
                    CategoryInfo(
                        name = category.name,
                        displayName = category.displayName,
                        description = category.description
                    )
                }
            return CategoriesResponse(categories)
        }
    }
}
