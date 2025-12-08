package me.onetwo.upvy.domain.report.model

/**
 * 신고 타입
 *
 * 사용자가 콘텐츠를 신고할 때 선택할 수 있는 신고 사유 타입입니다.
 */
enum class ReportType {
    /**
     * 주제에서 벗어남
     *
     * 공부 콘텐츠에서 벗어나거나 주제와 관련 없는 내용
     */
    OFF_TOPIC,

    /**
     * 스팸
     *
     * 광고, 홍보, 반복적인 무의미한 콘텐츠
     */
    SPAM,

    /**
     * 부적절한 콘텐츠
     *
     * 폭력적이거나 성적인 콘텐츠, 기타 부적절한 내용
     */
    INAPPROPRIATE_CONTENT,

    /**
     * 저작권 침해
     *
     * 타인의 저작물을 무단으로 사용한 콘텐츠
     */
    COPYRIGHT,

    /**
     * 괴롭힘
     *
     * 특정인을 대상으로 한 괴롭힘이나 명예훼손
     */
    HARASSMENT,

    /**
     * 혐오 발언
     *
     * 특정 집단에 대한 차별이나 혐오 표현
     */
    HATE_SPEECH,

    /**
     * 허위 정보
     *
     * 의도적으로 거짓 정보를 유포하는 콘텐츠
     */
    MISINFORMATION,

    /**
     * 기타
     *
     * 위 카테고리에 해당하지 않는 기타 사유
     */
    OTHER
}
