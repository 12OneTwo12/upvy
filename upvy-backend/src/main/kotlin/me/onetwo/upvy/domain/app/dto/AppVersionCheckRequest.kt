package me.onetwo.upvy.domain.app.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * 앱 버전 체크 요청 DTO
 *
 * 클라이언트가 현재 버전 정보와 플랫폼 정보를 전송하여
 * 강제 업데이트 필요 여부를 확인합니다.
 *
 * **참고**: 필드는 nullable 타입으로 선언하여 JSON 파싱 시
 * 누락된 필드를 validation 에러(400)로 처리할 수 있도록 합니다.
 * Non-nullable 타입 사용 시 Jackson deserialization 에러(500)가 발생합니다.
 *
 * @property platform 플랫폼 (IOS, ANDROID)
 * @property currentVersion 현재 앱 버전 (시맨틱 버전 형식: major.minor.patch)
 */
data class AppVersionCheckRequest(
    @field:NotBlank(message = "플랫폼은 필수입니다")
    @field:Pattern(regexp = "^(IOS|ANDROID)$", message = "플랫폼은 IOS 또는 ANDROID여야 합니다")
    val platform: String?,

    @field:NotBlank(message = "현재 버전은 필수입니다")
    @field:Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "버전은 시맨틱 버전 형식(major.minor.patch)이어야 합니다")
    val currentVersion: String?
)
