package me.onetwo.growsnap.domain.category.dto

import me.onetwo.growsnap.domain.content.model.Category

/**
 * 카테고리 정보 응답 DTO
 *
 * @property value 카테고리 값 (enum name)
 * @property displayName 카테고리 표시 이름 (한글)
 * @property description 카테고리 설명
 */
data class CategoryInfo(
    val value: String,
    val displayName: String,
    val description: String
) {
    companion object {
        /**
         * Category enum을 CategoryInfo로 변환
         *
         * @param category 카테고리 enum
         * @return 카테고리 정보 DTO
         */
        fun from(category: Category): CategoryInfo {
            return CategoryInfo(
                value = category.name,
                displayName = category.displayName,
                description = category.description
            )
        }
    }
}

/**
 * 카테고리 목록 응답 DTO
 *
 * @property categories 카테고리 정보 목록
 */
data class CategoriesResponse(
    val categories: List<CategoryInfo>
) {
    companion object {
        /**
         * Category enum 배열을 CategoriesResponse로 변환
         *
         * @param categories 카테고리 enum 목록
         * @return 카테고리 목록 응답 DTO
         */
        fun from(categories: List<Category>): CategoriesResponse {
            return CategoriesResponse(
                categories = categories.map { CategoryInfo.from(it) }
            )
        }
    }
}
