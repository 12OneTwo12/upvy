package me.onetwo.growsnap.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.format.FormatterRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * Spring WebFlux 설정
 *
 * WebFlux 관련 커스텀 설정을 관리합니다.
 * 현재는 PathVariable과 RequestParam의 타입 변환을 위한 Converter를 등록합니다.
 */
@Configuration
class WebFluxConfig : WebFluxConfigurer {

    /**
     * 커스텀 Formatter와 Converter 등록
     *
     * ### 등록된 Converter
     * - **StringToEnumConverterFactory**: 문자열을 Enum으로 변환 (대소문자 구분 없음)
     *   - 프론트엔드에서 소문자로 전달된 Enum 값을 자동으로 대문자로 변환
     *   - 예: "content" → TargetType.CONTENT
     *
     * @param registry FormatterRegistry
     */
    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverterFactory(StringToEnumConverterFactory())
    }
}
