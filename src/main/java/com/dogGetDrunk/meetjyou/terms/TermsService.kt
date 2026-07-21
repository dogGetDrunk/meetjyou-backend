package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.TermsNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.terms.DuplicateTermsVersionException
import com.dogGetDrunk.meetjyou.common.exception.business.terms.InactiveTermsAccessException
import com.dogGetDrunk.meetjyou.common.exception.business.terms.InvalidTermsAgreementException
import com.dogGetDrunk.meetjyou.common.exception.business.terms.InvalidTermsUuidException
import com.dogGetDrunk.meetjyou.common.exception.business.terms.MissingRequiredTermsAgreementException
import com.dogGetDrunk.meetjyou.common.exception.business.terms.TermsContentVerificationException
import com.dogGetDrunk.meetjyou.notification.event.TermsReconsentEvent
import com.dogGetDrunk.meetjyou.terms.dto.GetTermsContentUrlResponse
import com.dogGetDrunk.meetjyou.terms.dto.GetTermsResponse
import com.dogGetDrunk.meetjyou.terms.dto.PublishTermsRequest
import com.dogGetDrunk.meetjyou.terms.dto.TermsUploadPar
import com.dogGetDrunk.meetjyou.user.User
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class TermsService(
    private val termsRepository: TermsRepository,
    private val userTermsRepository: UserTermsRepository,
    private val termsContentUrlGenerator: TermsContentUrlGenerator,
    private val publisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(TermsService::class.java)

    @Transactional(readOnly = true)
    fun getAllActiveTerms(): List<GetTermsResponse> {
        log.info("Starting retrieval of active terms.")

        val now = Instant.now()
        val terms = termsRepository.findActiveTerms(TermsStatus.ACTIVE, now)

        log.info("Completed retrieval of active terms. count={}", terms.size)

        return terms.map(GetTermsResponse::of)
    }

    @Transactional(readOnly = true)
    fun getTermsContentUrl(termsUuidString: String): GetTermsContentUrlResponse {
        log.info("Starting retrieval of terms content url. termsUuid={}", termsUuidString)

        val termsUuid = parseUuid(termsUuidString)
        val terms = termsRepository.findByUuid(termsUuid)
            ?: throw TermsNotFoundException(termsUuidString)

        if (terms.status != TermsStatus.ACTIVE) {
            throw InactiveTermsAccessException(terms.uuid.toString())
        }

        val par = termsContentUrlGenerator.generateDownloadPar(terms.contentObjectKey)

        log.info(
            "Completed retrieval of terms content url. termsUuid={}, contentObjectKey={}",
            termsUuidString,
            terms.contentObjectKey,
        )

        return GetTermsContentUrlResponse(
            termsUuid = terms.uuid.toString(),
            downloadUrl = par.url,
            httpMethod = par.httpMethod,
            expiresAt = par.expiresAt,
        )
    }

    @Transactional(readOnly = true)
    fun validateRequiredTermsAgreement(agreedTermsUuids: List<UUID>): List<Terms> {
        log.info(
            "Starting validation of required terms agreement. agreedTermsCount={}",
            agreedTermsUuids.size,
        )

        val agreedTerms = termsRepository.findAllByUuidIn(agreedTermsUuids)

        if (agreedTerms.size != agreedTermsUuids.size) {
            throw InvalidTermsAgreementException(agreedTermsUuids.joinToString(","))
        }

        if (agreedTerms.any { it.status != TermsStatus.ACTIVE }) {
            throw InvalidTermsAgreementException(
                agreedTerms.joinToString(",") { it.uuid.toString() },
            )
        }

        val now = Instant.now()
        val requiredActiveTerms = termsRepository.findRequiredActiveTerms(TermsStatus.ACTIVE, now)
        val agreedTermsIdSet = agreedTerms.map { it.id }.toSet()

        val missingRequiredTerms = requiredActiveTerms.filter { it.id !in agreedTermsIdSet }
        if (missingRequiredTerms.isNotEmpty()) {
            val missingTermsValue = missingRequiredTerms.joinToString(",") { it.uuid.toString() }

            log.warn(
                "Required terms agreement validation failed. missingTerms={}",
                missingTermsValue,
            )

            throw MissingRequiredTermsAgreementException(missingTermsValue)
        }

        log.info("Completed validation of required terms agreement.")

        return agreedTerms
    }

    @Transactional
    fun saveUserTerms(
        user: User,
        agreedTerms: List<Terms>,
    ) {
        log.info(
            "Starting persistence of user terms agreement. userId={}, agreedTermsCount={}",
            user.id,
            agreedTerms.size,
        )

        val userTerms = agreedTerms.map { terms ->
            UserTerms(
                terms = terms,
                user = user,
            )
        }

        userTermsRepository.saveAll(userTerms)
        applyMarketingConsent(user, agreedTerms)

        log.info(
            "Completed persistence of user terms agreement. userId={}, savedCount={}",
            user.id,
            userTerms.size,
        )
    }

    @Transactional
    fun recordConsentChange(
        user: User,
        type: TermsType,
        agreed: Boolean,
    ) {
        val desiredAction = if (agreed) TermsAgreementAction.AGREED else TermsAgreementAction.WITHDRAWN
        val latest = userTermsRepository.findTopByUser_IdAndTerms_TypeOrderByIdDesc(user.id, type)
        if (latest?.action == desiredAction) {
            return
        }

        val terms = termsRepository.findByTypeAndStatus(type, TermsStatus.ACTIVE)
            ?: throw TermsNotFoundException(type.name)

        userTermsRepository.save(UserTerms(terms = terms, user = user, action = desiredAction))

        log.info(
            "Recorded terms agreement action change. userId={}, type={}, action={}",
            user.id,
            type,
            desiredAction,
        )
    }

    @Transactional(readOnly = true)
    fun createContentUploadPar(type: TermsType, version: String): TermsUploadPar {
        log.info("Creating terms content upload PAR. type={}, version={}", type, version)
        return termsContentUrlGenerator.generateUploadPar(type, version)
    }

    @Transactional
    fun publishTerms(request: PublishTermsRequest): GetTermsResponse {
        log.info("Starting terms publish. type={}, version={}", request.type, request.version)

        if (termsRepository.existsByTypeAndVersion(request.type, request.version)) {
            throw DuplicateTermsVersionException("${request.type} ${request.version}")
        }

        val objectKey = request.type.toObjectKey(request.version)
        if (!termsContentUrlGenerator.verifyContent(objectKey, request.contentHash)) {
            throw TermsContentVerificationException(objectKey)
        }

        val previousActiveTerms = termsRepository.findByTypeAndStatus(request.type, TermsStatus.ACTIVE)

        val newTerms = termsRepository.save(
            Terms(
                type = request.type,
                version = request.version,
                displayText = request.displayText,
                required = request.required,
                contentObjectKey = objectKey,
                contentHash = request.contentHash,
                effectiveAt = request.effectiveAt ?: Instant.now(),
            ),
        )

        if (previousActiveTerms != null) {
            supersede(previousActiveTerms, newTerms)
        }

        log.info("Completed terms publish. termsUuid={}", newTerms.uuid)

        return GetTermsResponse.of(newTerms)
    }

    private fun supersede(previousTerms: Terms, newTerms: Terms) {
        previousTerms.status = TermsStatus.INACTIVE
        termsRepository.save(previousTerms)

        publisher.publishEvent(
            TermsReconsentEvent(
                termsUuid = newTerms.uuid,
                termsType = newTerms.type,
                displayText = newTerms.displayText,
            ),
        )

        log.info(
            "Superseded previous terms and published reconsent event. previousTermsUuid={}, newTermsUuid={}",
            previousTerms.uuid,
            newTerms.uuid,
        )
    }

    private fun applyMarketingConsent(user: User, agreedTerms: List<Terms>) {
        val agreedTypes = agreedTerms.map { it.type }.toSet()
        user.marketingSnsConsented = TermsType.MARKETING_SNS_EVENTS in agreedTypes
        user.marketingEmailConsented = TermsType.MARKETING_EMAIL_EVENTS in agreedTypes
    }

    private fun parseUuid(uuidString: String): UUID {
        return try {
            UUID.fromString(uuidString)
        } catch (exception: IllegalArgumentException) {
            throw InvalidTermsUuidException(uuidString)
        }
    }
}
