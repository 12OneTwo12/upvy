package me.onetwo.growsnap.infrastructure.config

import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.converter.ConverterFactory
import java.lang.Enum.*

/**
 * 문자열을 Enum으로 변환하는 Converter Factory
 *
 * PathVariable이나 RequestParam으로 전달된 문자열을 대소문자 구분 없이 Enum으로 변환합니다.
 * 프론트엔드에서 소문자로 전달된 값도 자동으로 대문자 Enum으로 변환됩니다.
 *
 * ## 사용 예시
 * - 요청: `/api/v1/reports/content/{targetId}` (소문자 "content")
 * - 변환: `TargetType.CONTENT` (대문자 Enum)
 *
 * @param T Enum 타입
 */
class StringToEnumConverterFactory : ConverterFactory<String, Enum<*>> {

    /**
     * 특정 Enum 타입에 대한 Converter를 생성합니다.
     *
     * @param targetType 변환할 Enum 클래스
     * @return 문자열을 Enum으로 변환하는 Converter
     */
    override fun <T : Enum<*>> getConverter(targetType: Class<T>): Converter<String, T> {
        return StringToEnumConverter(targetType)
    }

    /**
     * 문자열을 특정 Enum 타입으로 변환하는 Converter
     *
     * ### 변환 규칙
     * 1. 입력 문자열을 대문자로 변환
     * 2. Enum의 name과 일치하는 값을 찾아 반환
     * 3. 일치하는 값이 없으면 IllegalArgumentException 발생
     *
     * @param T Enum 타입
     * @property enumType 변환할 Enum 클래스
     */
    private class StringToEnumConverter<T : Enum<*>>(
        private val enumType: Class<T>
    ) : Converter<String, T> {

        /**
         * 문자열을 Enum으로 변환합니다.
         *
         * ### 처리 흐름
         * 1. 입력 문자열을 trim하고 대문자로 변환
         * 2. Enum.valueOf()로 해당 Enum 값을 찾아 반환
         * 3. 존재하지 않는 값이면 IllegalArgumentException 발생
         *
         * @param source 변환할 문자열 (대소문자 구분 없음)
         * @return 변환된 Enum 값
         * @throws IllegalArgumentException 유효하지 않은 Enum 값인 경우
         */
        override fun convert(source: String): T {
            val normalizedSource = source.trim().uppercase()

            return try {
                @Suppress("UNCHECKED_CAST")
                valueOf(enumType as Class<out Enum<*>>, normalizedSource) as T
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Invalid value '$source' for enum type ${enumType.simpleName}. " +
                        "Valid values are: ${enumType.enumConstants.joinToString(", ") { it.name }}",
                    e
                )
            }
        }
    }
}
