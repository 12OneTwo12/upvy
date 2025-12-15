package me.onetwo.upvy.domain.auth.terms.repository

import me.onetwo.upvy.domain.auth.terms.model.UserTermsAgreement
import me.onetwo.upvy.domain.auth.terms.model.UserTermsAgreementHistory
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 약관 동의 Repository 인터페이스
 *
 * 사용자의 약관 동의 정보를 관리합니다.
 */
interface TermsAgreementRepository {
    /**
     * 사용자 ID로 약관 동의 정보 조회
     *
     * @param userId 사용자 ID
     * @return 약관 동의 정보 (없으면 empty Mono)
     */
    fun findByUserId(userId: UUID): Mono<UserTermsAgreement>

    /**
     * 약관 동의 정보 저장 또는 업데이트
     *
     * userId가 이미 존재하면 UPDATE, 없으면 INSERT
     *
     * @param agreement 저장할 약관 동의 정보
     * @return 저장된 약관 동의 정보
     */
    fun save(agreement: UserTermsAgreement): Mono<UserTermsAgreement>

    /**
     * 약관 동의 이력 저장
     *
     * 법적 증빙을 위해 모든 동의/철회 이력을 보관합니다.
     *
     * @param history 저장할 약관 동의 이력
     * @return 완료 신호
     */
    fun saveHistory(history: UserTermsAgreementHistory): Mono<Void>
}
