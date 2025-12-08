package me.onetwo.upvy.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * 이미지 업로드 관련 설정
 *
 * application.yml의 app.upload 설정을 바인딩합니다.
 *
 * @property maxFileSize 최대 파일 크기 (바이트)
 * @property allowedImageTypes 허용되는 이미지 타입 목록
 * @property profileImage 프로필 이미지 관련 설정
 */
@Component
@ConfigurationProperties(prefix = "app.upload")
data class ImageUploadProperties(
    var maxFileSize: Long = 5242880, // 5MB
    var allowedImageTypes: List<String> = listOf("image/jpeg", "image/jpg", "image/png", "image/webp"),
    var profileImage: ProfileImageConfig = ProfileImageConfig()
) {
    /**
     * 프로필 이미지 설정
     *
     * @property maxWidth 최대 너비 (픽셀)
     * @property maxHeight 최대 높이 (픽셀)
     * @property quality 이미지 품질 (0.0 ~ 1.0)
     */
    data class ProfileImageConfig(
        var maxWidth: Int = 500,
        var maxHeight: Int = 500,
        var quality: Double = 0.85
    )
}
