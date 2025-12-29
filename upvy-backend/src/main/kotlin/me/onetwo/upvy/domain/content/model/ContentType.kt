package me.onetwo.upvy.domain.content.model

/**
 * 콘텐츠 타입
 *
 * 플랫폼에서 지원하는 콘텐츠의 미디어 타입을 정의합니다.
 */
enum class ContentType {
    /**
     * 비디오 콘텐츠
     */
    VIDEO,

    /**
     * 사진 콘텐츠
     */
    PHOTO,

    /**
     * 퀴즈 콘텐츠
     *
     * 교육용 객관식 퀴즈 콘텐츠입니다.
     * 비디오나 사진 없이 퀴즈만으로 구성된 콘텐츠입니다.
     */
    QUIZ
}
