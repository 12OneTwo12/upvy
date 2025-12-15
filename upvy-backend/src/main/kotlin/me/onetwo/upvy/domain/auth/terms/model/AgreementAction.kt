package me.onetwo.upvy.domain.auth.terms.model

/**
 * 약관 동의 동작 타입
 *
 * @property AGREED 최초 동의
 * @property UPDATED 재동의 (약관 버전 변경 시)
 * @property REVOKED 동의 철회 (마케팅 동의 등 선택 약관)
 */
enum class AgreementAction {
    AGREED,
    UPDATED,
    REVOKED
}
