package me.onetwo.upvy.infrastructure.config

import org.springframework.boot.test.autoconfigure.restdocs.RestDocsWebTestClientConfigurationCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.restdocs.operation.preprocess.Preprocessors

/**
 * REST Docs 테스트 설정
 *
 * Spring REST Docs 문서 생성을 위한 WebFlux 설정입니다.
 * WebTestClient 기반으로 API 문서를 자동 생성합니다.
 */
@TestConfiguration
class RestDocsConfiguration {

    /**
     * REST Docs WebTestClient 커스터마이저
     *
     * 요청/응답을 예쁘게 출력하도록 설정합니다.
     */
    @Bean
    fun restDocsWebTestClientConfigurationCustomizer() =
        RestDocsWebTestClientConfigurationCustomizer { configurer ->
            configurer.operationPreprocessors()
                .withRequestDefaults(Preprocessors.prettyPrint())
                .withResponseDefaults(Preprocessors.prettyPrint())
        }
}
