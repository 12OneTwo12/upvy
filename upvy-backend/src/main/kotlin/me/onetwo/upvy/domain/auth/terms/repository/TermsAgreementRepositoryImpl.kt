package me.onetwo.upvy.domain.auth.terms.repository

import me.onetwo.upvy.domain.auth.terms.model.AgreementAction
import me.onetwo.upvy.domain.auth.terms.model.TermsType
import me.onetwo.upvy.domain.auth.terms.model.UserTermsAgreement
import me.onetwo.upvy.domain.auth.terms.model.UserTermsAgreementHistory
import me.onetwo.upvy.jooq.generated.tables.references.USER_TERMS_AGREEMENT_HISTORY
import me.onetwo.upvy.jooq.generated.tables.references.USER_TERMS_AGREEMENTS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 약관 동의 Repository 구현체
 *
 * JOOQ R2DBC를 사용하여 약관 동의 정보를 관리합니다.
 *
 * @property dslContext JOOQ DSL Context
 */
@Repository
class TermsAgreementRepositoryImpl(
    private val dslContext: DSLContext
) : TermsAgreementRepository {

    override fun findByUserId(userId: UUID): Mono<UserTermsAgreement> {
        return Mono.from(
            dslContext
                .select(
                    USER_TERMS_AGREEMENTS.ID,
                    USER_TERMS_AGREEMENTS.USER_ID,
                    USER_TERMS_AGREEMENTS.SERVICE_TERMS_AGREED,
                    USER_TERMS_AGREEMENTS.SERVICE_TERMS_VERSION,
                    USER_TERMS_AGREEMENTS.SERVICE_TERMS_AGREED_AT,
                    USER_TERMS_AGREEMENTS.PRIVACY_POLICY_AGREED,
                    USER_TERMS_AGREEMENTS.PRIVACY_POLICY_VERSION,
                    USER_TERMS_AGREEMENTS.PRIVACY_POLICY_AGREED_AT,
                    USER_TERMS_AGREEMENTS.COMMUNITY_GUIDELINES_AGREED,
                    USER_TERMS_AGREEMENTS.COMMUNITY_GUIDELINES_VERSION,
                    USER_TERMS_AGREEMENTS.COMMUNITY_GUIDELINES_AGREED_AT,
                    USER_TERMS_AGREEMENTS.MARKETING_AGREED,
                    USER_TERMS_AGREEMENTS.MARKETING_VERSION,
                    USER_TERMS_AGREEMENTS.MARKETING_AGREED_AT,
                    USER_TERMS_AGREEMENTS.CREATED_AT,
                    USER_TERMS_AGREEMENTS.CREATED_BY,
                    USER_TERMS_AGREEMENTS.UPDATED_AT,
                    USER_TERMS_AGREEMENTS.UPDATED_BY,
                    USER_TERMS_AGREEMENTS.DELETED_AT
                )
                .from(USER_TERMS_AGREEMENTS)
                .where(USER_TERMS_AGREEMENTS.USER_ID.eq(userId.toString()))
                .and(USER_TERMS_AGREEMENTS.DELETED_AT.isNull)
        ).map { record ->
            UserTermsAgreement(
                id = record.get(USER_TERMS_AGREEMENTS.ID)?.let { UUID.fromString(it) },
                userId = UUID.fromString(record.get(USER_TERMS_AGREEMENTS.USER_ID)),
                serviceTermsAgreed = record.get(USER_TERMS_AGREEMENTS.SERVICE_TERMS_AGREED) ?: false,
                serviceTermsVersion = record.get(USER_TERMS_AGREEMENTS.SERVICE_TERMS_VERSION),
                serviceTermsAgreedAt = record.get(USER_TERMS_AGREEMENTS.SERVICE_TERMS_AGREED_AT),
                privacyPolicyAgreed = record.get(USER_TERMS_AGREEMENTS.PRIVACY_POLICY_AGREED) ?: false,
                privacyPolicyVersion = record.get(USER_TERMS_AGREEMENTS.PRIVACY_POLICY_VERSION),
                privacyPolicyAgreedAt = record.get(USER_TERMS_AGREEMENTS.PRIVACY_POLICY_AGREED_AT),
                communityGuidelinesAgreed = record.get(USER_TERMS_AGREEMENTS.COMMUNITY_GUIDELINES_AGREED) ?: false,
                communityGuidelinesVersion = record.get(USER_TERMS_AGREEMENTS.COMMUNITY_GUIDELINES_VERSION),
                communityGuidelinesAgreedAt = record.get(USER_TERMS_AGREEMENTS.COMMUNITY_GUIDELINES_AGREED_AT),
                marketingAgreed = record.get(USER_TERMS_AGREEMENTS.MARKETING_AGREED) ?: false,
                marketingVersion = record.get(USER_TERMS_AGREEMENTS.MARKETING_VERSION),
                marketingAgreedAt = record.get(USER_TERMS_AGREEMENTS.MARKETING_AGREED_AT),
                createdAt = record.get(USER_TERMS_AGREEMENTS.CREATED_AT) ?: Instant.now(),
                createdBy = record.get(USER_TERMS_AGREEMENTS.CREATED_BY),
                updatedAt = record.get(USER_TERMS_AGREEMENTS.UPDATED_AT) ?: Instant.now(),
                updatedBy = record.get(USER_TERMS_AGREEMENTS.UPDATED_BY),
                deletedAt = record.get(USER_TERMS_AGREEMENTS.DELETED_AT)
            )
        }
    }

    override fun save(agreement: UserTermsAgreement): Mono<UserTermsAgreement> {
        val now = Instant.now()
        val agreementId = agreement.id ?: UUID.randomUUID()

        return Mono.from(
            dslContext
                .insertInto(USER_TERMS_AGREEMENTS)
                .set(USER_TERMS_AGREEMENTS.ID, agreementId.toString())
                .set(USER_TERMS_AGREEMENTS.USER_ID, agreement.userId.toString())
                .set(USER_TERMS_AGREEMENTS.SERVICE_TERMS_AGREED, agreement.serviceTermsAgreed)
                .set(USER_TERMS_AGREEMENTS.SERVICE_TERMS_VERSION, agreement.serviceTermsVersion)
                .set(USER_TERMS_AGREEMENTS.SERVICE_TERMS_AGREED_AT, agreement.serviceTermsAgreedAt)
                .set(USER_TERMS_AGREEMENTS.PRIVACY_POLICY_AGREED, agreement.privacyPolicyAgreed)
                .set(USER_TERMS_AGREEMENTS.PRIVACY_POLICY_VERSION, agreement.privacyPolicyVersion)
                .set(USER_TERMS_AGREEMENTS.PRIVACY_POLICY_AGREED_AT, agreement.privacyPolicyAgreedAt)
                .set(USER_TERMS_AGREEMENTS.COMMUNITY_GUIDELINES_AGREED, agreement.communityGuidelinesAgreed)
                .set(USER_TERMS_AGREEMENTS.COMMUNITY_GUIDELINES_VERSION, agreement.communityGuidelinesVersion)
                .set(USER_TERMS_AGREEMENTS.COMMUNITY_GUIDELINES_AGREED_AT, agreement.communityGuidelinesAgreedAt)
                .set(USER_TERMS_AGREEMENTS.MARKETING_AGREED, agreement.marketingAgreed)
                .set(USER_TERMS_AGREEMENTS.MARKETING_VERSION, agreement.marketingVersion)
                .set(USER_TERMS_AGREEMENTS.MARKETING_AGREED_AT, agreement.marketingAgreedAt)
                .set(USER_TERMS_AGREEMENTS.CREATED_AT, now)
                .set(USER_TERMS_AGREEMENTS.CREATED_BY, agreement.userId.toString())
                .set(USER_TERMS_AGREEMENTS.UPDATED_AT, now)
                .set(USER_TERMS_AGREEMENTS.UPDATED_BY, agreement.userId.toString())
                .onDuplicateKeyUpdate()
                .set(USER_TERMS_AGREEMENTS.SERVICE_TERMS_AGREED, agreement.serviceTermsAgreed)
                .set(USER_TERMS_AGREEMENTS.SERVICE_TERMS_VERSION, agreement.serviceTermsVersion)
                .set(USER_TERMS_AGREEMENTS.SERVICE_TERMS_AGREED_AT, agreement.serviceTermsAgreedAt)
                .set(USER_TERMS_AGREEMENTS.PRIVACY_POLICY_AGREED, agreement.privacyPolicyAgreed)
                .set(USER_TERMS_AGREEMENTS.PRIVACY_POLICY_VERSION, agreement.privacyPolicyVersion)
                .set(USER_TERMS_AGREEMENTS.PRIVACY_POLICY_AGREED_AT, agreement.privacyPolicyAgreedAt)
                .set(USER_TERMS_AGREEMENTS.COMMUNITY_GUIDELINES_AGREED, agreement.communityGuidelinesAgreed)
                .set(USER_TERMS_AGREEMENTS.COMMUNITY_GUIDELINES_VERSION, agreement.communityGuidelinesVersion)
                .set(USER_TERMS_AGREEMENTS.COMMUNITY_GUIDELINES_AGREED_AT, agreement.communityGuidelinesAgreedAt)
                .set(USER_TERMS_AGREEMENTS.MARKETING_AGREED, agreement.marketingAgreed)
                .set(USER_TERMS_AGREEMENTS.MARKETING_VERSION, agreement.marketingVersion)
                .set(USER_TERMS_AGREEMENTS.MARKETING_AGREED_AT, agreement.marketingAgreedAt)
                .set(USER_TERMS_AGREEMENTS.UPDATED_AT, now)
                .set(USER_TERMS_AGREEMENTS.UPDATED_BY, agreement.userId.toString())
        ).thenReturn(
            agreement.copy(
                id = agreementId,
                createdAt = now,
                createdBy = agreement.userId.toString(),
                updatedAt = now,
                updatedBy = agreement.userId.toString()
            )
        )
    }

    override fun saveHistory(history: UserTermsAgreementHistory): Mono<Void> {
        val historyId = history.id ?: UUID.randomUUID()
        val now = Instant.now()

        return Mono.from(
            dslContext
                .insertInto(USER_TERMS_AGREEMENT_HISTORY)
                .set(USER_TERMS_AGREEMENT_HISTORY.ID, historyId.toString())
                .set(USER_TERMS_AGREEMENT_HISTORY.USER_ID, history.userId.toString())
                .set(USER_TERMS_AGREEMENT_HISTORY.TERMS_TYPE, history.termsType.name)
                .set(USER_TERMS_AGREEMENT_HISTORY.TERMS_VERSION, history.termsVersion)
                .set(USER_TERMS_AGREEMENT_HISTORY.ACTION, history.action.name)
                .set(USER_TERMS_AGREEMENT_HISTORY.AGREED_AT, history.agreedAt)
                .set(USER_TERMS_AGREEMENT_HISTORY.IP_ADDRESS, history.ipAddress)
                .set(USER_TERMS_AGREEMENT_HISTORY.USER_AGENT, history.userAgent)
                .set(USER_TERMS_AGREEMENT_HISTORY.CREATED_AT, now)
        ).then()
    }
}
