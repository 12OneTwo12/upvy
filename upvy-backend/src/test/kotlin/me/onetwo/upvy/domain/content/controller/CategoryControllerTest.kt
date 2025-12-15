package me.onetwo.upvy.domain.content.controller

import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.infrastructure.config.RestDocsConfiguration
import me.onetwo.upvy.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

@WebFluxTest(CategoryController::class)
@Import(RestDocsConfiguration::class, TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("카테고리 Controller 테스트")
class CategoryControllerTest : BaseReactiveTest() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    @DisplayName("카테고리 목록 조회 시, OTHER를 제외한 모든 카테고리를 반환한다")
    fun getCategories_ReturnsAllCategoriesExceptOther() {
        // Given: 인증된 사용자
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

        // When & Then: 카테고리 목록 조회
        webTestClient
            .mutateWith(mockUser(userId))
            .get()
            .uri(ApiPaths.API_V1_CATEGORIES)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.categories").isArray
            .jsonPath("$.categories.length()").value<Int> { count ->
                assert(count > 0) { "카테고리 목록이 비어있습니다" }
            }
            .jsonPath("$.categories[0].name").exists()
            .jsonPath("$.categories[0].displayName").exists()
            .jsonPath("$.categories[0].description").exists()
            .consumeWith(
                document("categories",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("categories[]").description("카테고리 목록 (OTHER 제외)"),
                        fieldWithPath("categories[].name").description("카테고리 코드 (enum name)"),
                        fieldWithPath("categories[].displayName").description("화면 표시용 이름"),
                        fieldWithPath("categories[].description").description("카테고리 설명")
                    )
                )
            )
    }

    @Test
    @DisplayName("카테고리 목록에 OTHER가 포함되지 않는다")
    fun getCategories_DoesNotContainOther() {
        // Given: 인증된 사용자
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

        // When & Then: OTHER 카테고리가 제외되었는지 확인
        webTestClient
            .mutateWith(mockUser(userId))
            .get()
            .uri(ApiPaths.API_V1_CATEGORIES)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.categories[?(@.name == 'OTHER')]").doesNotExist()
    }
}
