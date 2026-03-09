package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.TermsNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.terms.InactiveTermsAccessException
import com.dogGetDrunk.meetjyou.common.exception.business.terms.InvalidTermsAgreementException
import com.dogGetDrunk.meetjyou.common.exception.business.terms.InvalidTermsUuidException
import com.dogGetDrunk.meetjyou.common.exception.business.terms.MissingRequiredTermsAgreementException
import com.dogGetDrunk.meetjyou.terms.dto.GetTermsContentUrlResponse
import com.dogGetDrunk.meetjyou.terms.dto.GetTermsResponse
import com.dogGetDrunk.meetjyou.user.User
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class TermsService(
    private val termsRepository: TermsRepository,
    private val userTermsRepository: UserTermsRepository,
    private val termsContentUrlGenerator: TermsContentUrlGenerator,
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

        log.info(
            "Completed persistence of user terms agreement. userId={}, savedCount={}",
            user.id,
            userTerms.size,
        )
    }

    private fun parseUuid(uuidString: String): UUID {
        return try {
            UUID.fromString(uuidString)
        } catch (exception: IllegalArgumentException) {
            throw InvalidTermsUuidException(uuidString)
        }
    }
}
