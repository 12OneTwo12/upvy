package me.onetwo.upvy.domain.auth.terms.service

import me.onetwo.upvy.domain.auth.terms.dto.TermsAgreementRequest
import me.onetwo.upvy.domain.auth.terms.dto.TermsAgreementResponse
import me.onetwo.upvy.domain.auth.terms.model.AgreementAction
import me.onetwo.upvy.domain.auth.terms.model.TermsType
import me.onetwo.upvy.domain.auth.terms.model.TermsVersions
import me.onetwo.upvy.domain.auth.terms.model.UserTermsAgreement
import me.onetwo.upvy.domain.auth.terms.model.UserTermsAgreementHistory
import me.onetwo.upvy.domain.auth.terms.repository.TermsAgreementRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 약관 동의 처리 컨텍스트
 *
 * buildHistories 함수의 파라미터를 그룹화하여 가독성과 유지보수성을 개선합니다.
 *
 * @property userId 사용자 ID
 * @property request 약관 동의 요청
 * @property agreedAt 동의 시각
 * @property ipAddress IP 주소
 * @property userAgent User Agent
 * @property existing 기존 약관 동의 정보
 */
private data class AgreementContext(
    val userId: UUID,
    val request: TermsAgreementRequest,
    val agreedAt: Instant,
    val ipAddress: String?,
    val userAgent: String?,
    val existing: UserTermsAgreement
)

/**
 * 약관 동의 Service 구현체
 *
 * 약관 동의 관련 비즈니스 로직을 처리합니다.
 *
 * @property termsAgreementRepository 약관 동의 Repository
 */
@Service
class TermsAgreementServiceImpl(
    private val termsAgreementRepository: TermsAgreementRepository
) : TermsAgreementService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun agreeToTerms(
        userId: UUID,
        request: TermsAgreementRequest,
        ipAddress: String?,
        userAgent: String?
    ): Mono<TermsAgreementResponse> {
        logger.info("Processing terms agreement for userId={}", userId)

        val now = Instant.now()

        // 기존 약관 동의 조회
        return termsAgreementRepository.findByUserId(userId)
            .defaultIfEmpty(createDefaultAgreement(userId))
            .flatMap { existing ->
                // 약관 동의 정보 생성
                val updated = UserTermsAgreement(
                    id = existing.id,
                    userId = userId,
                    serviceTermsAgreed = request.serviceTermsAgreed,
                    serviceTermsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                    serviceTermsAgreedAt = now,
                    privacyPolicyAgreed = request.privacyPolicyAgreed,
                    privacyPolicyVersion = TermsVersions.CURRENT_PRIVACY_POLICY_VERSION,
                    privacyPolicyAgreedAt = now,
                    communityGuidelinesAgreed = request.communityGuidelinesAgreed,
                    communityGuidelinesVersion = TermsVersions.CURRENT_COMMUNITY_GUIDELINES_VERSION,
                    communityGuidelinesAgreedAt = now,
                    marketingAgreed = request.marketingAgreed,
                    marketingVersion = if (request.marketingAgreed) TermsVersions.CURRENT_MARKETING_VERSION else null,
                    marketingAgreedAt = if (request.marketingAgreed) now else null
                )

                // 약관 동의 저장
                termsAgreementRepository.save(updated)
                    .flatMap { saved ->
                        // 약관 동의 이력 저장
                        val context = AgreementContext(
                            userId = userId,
                            request = request,
                            agreedAt = now,
                            ipAddress = ipAddress,
                            userAgent = userAgent,
                            existing = existing
                        )
                        val histories = buildHistories(context)
                        Flux.fromIterable(histories)
                            .flatMap { termsAgreementRepository.saveHistory(it) }
                            .then(Mono.just(saved))
                    }
            }
            .map { TermsAgreementResponse.from(it) }
            .doOnSuccess { logger.info("Terms agreement completed for userId={}", userId) }
            .doOnError { logger.error("Failed to process terms agreement for userId={}", userId, it) }
    }

    override fun getTermsAgreement(userId: UUID): Mono<TermsAgreementResponse> {
        logger.debug("Fetching terms agreement for userId={}", userId)

        return termsAgreementRepository.findByUserId(userId)
            .map { TermsAgreementResponse.from(it) }
            .defaultIfEmpty(
                TermsAgreementResponse(
                    userId = userId.toString(),
                    serviceTermsAgreed = false,
                    serviceTermsVersion = null,
                    serviceTermsAgreedAt = null,
                    privacyPolicyAgreed = false,
                    privacyPolicyVersion = null,
                    privacyPolicyAgreedAt = null,
                    communityGuidelinesAgreed = false,
                    communityGuidelinesVersion = null,
                    communityGuidelinesAgreedAt = null,
                    marketingAgreed = false,
                    marketingVersion = null,
                    marketingAgreedAt = null,
                    isAllRequiredAgreed = false
                )
            )
            .doOnSuccess { logger.debug("Terms agreement retrieved for userId={}", userId) }
    }

    /**
     * 기본 약관 동의 생성 (모든 필드 false)
     *
     * @param userId 사용자 ID
     * @return 기본 약관 동의
     */
    private fun createDefaultAgreement(userId: UUID): UserTermsAgreement {
        return UserTermsAgreement(
            userId = userId,
            serviceTermsAgreed = false,
            privacyPolicyAgreed = false,
            communityGuidelinesAgreed = false,
            marketingAgreed = false
        )
    }

    /**
     * 약관 동의 이력 생성
     *
     * 동의한 약관에 대해서만 이력을 생성합니다.
     * 기존 동의가 있었는지 확인하여 AGREED 또는 UPDATED 액션을 결정합니다.
     *
     * @param context 약관 동의 처리 컨텍스트
     * @return 약관 동의 이력 목록
     */
    private fun buildHistories(
        context: AgreementContext
    ): List<UserTermsAgreementHistory> {
        val histories = mutableListOf<UserTermsAgreementHistory>()

        // 서비스 이용약관
        if (context.request.serviceTermsAgreed) {
            histories.add(
                UserTermsAgreementHistory(
                    userId = context.userId,
                    termsType = TermsType.SERVICE_TERMS,
                    termsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                    action = if (context.existing.serviceTermsAgreed) AgreementAction.UPDATED else AgreementAction.AGREED,
                    agreedAt = context.agreedAt,
                    ipAddress = context.ipAddress,
                    userAgent = context.userAgent
                )
            )
        }

        // 개인정보 처리방침
        if (context.request.privacyPolicyAgreed) {
            histories.add(
                UserTermsAgreementHistory(
                    userId = context.userId,
                    termsType = TermsType.PRIVACY_POLICY,
                    termsVersion = TermsVersions.CURRENT_PRIVACY_POLICY_VERSION,
                    action = if (context.existing.privacyPolicyAgreed) AgreementAction.UPDATED else AgreementAction.AGREED,
                    agreedAt = context.agreedAt,
                    ipAddress = context.ipAddress,
                    userAgent = context.userAgent
                )
            )
        }

        // 커뮤니티 가이드라인
        if (context.request.communityGuidelinesAgreed) {
            histories.add(
                UserTermsAgreementHistory(
                    userId = context.userId,
                    termsType = TermsType.COMMUNITY_GUIDELINES,
                    termsVersion = TermsVersions.CURRENT_COMMUNITY_GUIDELINES_VERSION,
                    action = if (context.existing.communityGuidelinesAgreed) AgreementAction.UPDATED else AgreementAction.AGREED,
                    agreedAt = context.agreedAt,
                    ipAddress = context.ipAddress,
                    userAgent = context.userAgent
                )
            )
        }

        // 마케팅 정보 수신 동의 (선택)
        if (context.request.marketingAgreed != context.existing.marketingAgreed) {
            histories.add(
                UserTermsAgreementHistory(
                    userId = context.userId,
                    termsType = TermsType.MARKETING_CONSENT,
                    termsVersion = TermsVersions.CURRENT_MARKETING_VERSION,
                    action = when {
                        context.request.marketingAgreed -> AgreementAction.AGREED
                        else -> AgreementAction.REVOKED
                    },
                    agreedAt = context.agreedAt,
                    ipAddress = context.ipAddress,
                    userAgent = context.userAgent
                )
            )
        }

        return histories
    }
}
