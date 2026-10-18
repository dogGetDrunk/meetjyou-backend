package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.user.DuplicateNicknameException
import com.dogGetDrunk.meetjyou.common.exception.business.user.UserAlreadyExistsException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.preference.Preference
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.UserPreference
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.terms.TermsService
import com.dogGetDrunk.meetjyou.terms.TermsType
import com.dogGetDrunk.meetjyou.user.dto.BasicUserResponse
import com.dogGetDrunk.meetjyou.user.dto.PublicUserResponse
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.dto.UserPreferenceData
import com.dogGetDrunk.meetjyou.user.dto.UserUpdateRequest
import com.dogGetDrunk.meetjyou.user.dto.normalizeOrNull
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val preferenceRepository: PreferenceRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val termsService: TermsService,
    private val userSessionRevoker: UserSessionRevoker,
) {
    private val log = LoggerFactory.getLogger(UserService::class.java)

    companion object {
        private const val PREFERENCE_NOT_FOUND = "Preference not found in DB"
        private val IDENTITY_CONSTRAINTS =
            setOf(UserUniqueConstraints.EXTERNAL_ID, UserUniqueConstraints.SOCIAL_IDENTITY)
    }

    /** Creates the user with its social account, preferences and terms agreement. */
    @Transactional
    fun createUser(request: RegistrationRequest, principal: SocialPrincipal): User {
        val agreedTerms = termsService.validateRequiredTermsAgreement(request.agreedTermsUuids)
        validateNicknameAvailable(request.nickname)

        val createdUser = translateUniqueViolation(request.nickname, principal) {
            userRepository.saveAndFlush(buildUser(request, principal))
        }

        saveUserPreference(createdUser, request.gender.name, PreferenceType.GENDER)
        saveUserPreference(createdUser, request.age.name, PreferenceType.AGE)

        saveUserPreferences(createdUser, request.personalities.map { it.name }, PreferenceType.PERSONALITY)
        saveUserPreferences(createdUser, request.travelStyles.map { it.name }, PreferenceType.TRAVEL_STYLE)
        saveUserPreferences(createdUser, request.diet.map { it.name }, PreferenceType.DIET)
        saveUserPreferences(createdUser, request.etc.map { it.name }, PreferenceType.ETC)
        termsService.saveUserTerms(createdUser, agreedTerms)

        log.info("User saved successfully. uuid: {}, email: {}", createdUser.uuid, createdUser.email)

        return createdUser
    }

    private fun buildUser(request: RegistrationRequest, principal: SocialPrincipal): User {
        val user = User(
            email = principal.email,
            nickname = request.nickname,
            authProvider = request.authProvider,
            externalId = principal.subject,
        )
        user.bio = request.bio.normalizeOrNull()
        user.addSocialAccount(principal.authProvider, principal.subject, principal.email)
        return user
    }

    @Transactional
    fun withdrawUser() {
        val uuid = currentUserProvider.uuid
        val user = userRepository.findByUuid(uuid) ?: throw UserNotFoundException(uuid)

        log.info("Processing user withdrawal (user uuid: {})", uuid)
        user.withdraw(Instant.now())
        userSessionRevoker.revokeAll(user)
        log.info("User withdrawal completed (user uuid: {})", uuid)
    }

    @Transactional
    fun updateUser(request: UserUpdateRequest): BasicUserResponse {
        val user = currentUserProvider.user
        if (request.nickname != user.nickname) {
            validateNicknameAvailable(request.nickname)
            user.nickname = request.nickname
            translateUniqueViolation(request.nickname, principal = null) { userRepository.flush() }
        }
        user.bio = request.bio.normalizeOrNull()

        updateUserPreference(user, request.gender.name, PreferenceType.GENDER)
        updateUserPreference(user, request.age.name, PreferenceType.AGE)
        updateUserPreferences(user, request.personalities.map { it.name }, PreferenceType.PERSONALITY)
        updateUserPreferences(user, request.travelStyles.map { it.name }, PreferenceType.TRAVEL_STYLE)
        updateUserPreferences(user, request.diet.map { it.name }, PreferenceType.DIET)
        updateUserPreferences(user, request.etc.map { it.name }, PreferenceType.ETC)

        userRepository.save(user)
        log.info("User profile updated. uuid={}", user.uuid)
        return getUserProfile(user.uuid)
    }

    @Transactional(readOnly = true)
    fun getUserProfile(uuid: UUID): BasicUserResponse {
        val user = userRepository.findByUuid(uuid) ?: throw UserNotFoundException(uuid)
        return BasicUserResponse.of(user, loadPreferences(user.id))
    }

    // Withdrawn users have no public profile: hidden from the withdrawal request on (DELETED),
    // and their personal data is gone once purged.
    @Transactional(readOnly = true)
    fun getPublicUserProfile(uuid: UUID): PublicUserResponse {
        val user = userRepository.findByUuid(uuid)?.takeIf { it.isActive() } ?: throw UserNotFoundException(uuid)
        return PublicUserResponse.of(user, loadPreferences(user.id))
    }

    // Admin view keeps DELETED users (restore requests) but skips PURGED rows, which hold no personal data.
    @Transactional(readOnly = true)
    fun getAllUsersProfile(pageable: Pageable): Page<BasicUserResponse> {
        val users = userRepository.findAllByStatusNot(UserStatus.PURGED, pageable)
        val prefsMap = if (users.isEmpty) {
            emptyMap()
        } else {
            userPreferenceRepository.findAllByUser_IdIn(users.content.map { it.id }).groupBy { it.user.id }
        }
        return users.map { BasicUserResponse.of(it, UserPreferenceData.from(prefsMap[it.id] ?: emptyList())) }
    }

    @Transactional
    fun confirmProfileImage() {
        currentUserProvider.user.hasProfileImage = true
    }

    @Transactional
    fun clearProfileImage() {
        currentUserProvider.user.hasProfileImage = false
    }

    @Transactional
    fun updateMarketingConsent(snsConsented: Boolean, emailConsented: Boolean) {
        val user = currentUserProvider.user
        user.marketingSnsConsented = snsConsented
        user.marketingEmailConsented = emailConsented
        termsService.recordConsentChange(user, TermsType.MARKETING_SNS_EVENTS, snsConsented)
        termsService.recordConsentChange(user, TermsType.MARKETING_EMAIL_EVENTS, emailConsented)
    }

    // Same rule as validateNicknameAvailable, on purpose: answering "available" for a nickname
    // the next sign-up then rejects is worse than freeing it late. A withdrawn account's nickname
    // is released when the purge job anonymizes the row, at most a day after its grace period.
    @Transactional(readOnly = true)
    fun isDuplicateNickname(nickname: String): Boolean = userRepository.existsByNickname(nickname)

    // Checks the raw unique constraint: the nickname column is UNIQUE in the DB, so any existing
    // row, including one held by a withdrawn account, is taken.
    private fun validateNicknameAvailable(nickname: String) {
        if (userRepository.existsByNickname(nickname)) {
            throw DuplicateNicknameException(nickname)
        }
    }

    /**
     * Concurrent requests can all pass validateNicknameAvailable; the unique indexes decide. [block]
     * must flush, so a violation surfaces here and not at a later query or the commit, where it
     * would end as a 500. The loser gets a 409; an unknown constraint is rethrown as is, because
     * it is a bug, not a duplicate.
     */
    private fun <T> translateUniqueViolation(nickname: String, principal: SocialPrincipal?, block: () -> T): T =
        try {
            block()
        } catch (e: DataIntegrityViolationException) {
            throw duplicateExceptionFor(e, nickname, principal)
        }

    private fun duplicateExceptionFor(
        e: DataIntegrityViolationException,
        nickname: String,
        principal: SocialPrincipal?,
    ): RuntimeException {
        val constraint = e.violatedConstraintName()
        log.info("Concurrent request lost a unique constraint race. constraint: {}", constraint)
        return when {
            constraint == UserUniqueConstraints.NICKNAME -> DuplicateNicknameException(nickname)
            principal != null && constraint in IDENTITY_CONSTRAINTS -> UserAlreadyExistsException(
                principal.authProvider.name,
                principal.authProvider,
                message = "Concurrent sign-up for the same social identity",
            )
            else -> e
        }
    }

    // Names come from validated enum constants (e.g. Personality.name), so a missing DB row means
    // the `preference` seed data has drifted out of sync with the enum - a server-side data bug,
    // not bad input. Fail loudly here instead of silently dropping the selection: dropping it would
    // surface later as a confusing 404 when this (or another) user's profile is read.
    private fun requirePreferences(names: List<String>, type: PreferenceType): Map<String, Preference> {
        val preferenceByName = preferenceRepository.findAllByTypeAndNameIn(type, names).associateBy { it.name }
        val missingNames = names.filterNot { preferenceByName.containsKey(it) }
        if (missingNames.isNotEmpty()) {
            throw IllegalStateException("$PREFERENCE_NOT_FOUND names=$missingNames, type=$type")
        }
        return preferenceByName
    }

    fun saveUserPreference(user: User, preferenceName: String, type: PreferenceType) {
        val preference = requirePreferences(listOf(preferenceName), type).getValue(preferenceName)
        userPreferenceRepository.save(UserPreference(user, preference))
    }

    fun saveUserPreferences(user: User, preferenceNames: List<String>, type: PreferenceType) {
        if (preferenceNames.isEmpty()) return
        val preferenceByName = requirePreferences(preferenceNames, type)
        val userPreferences = preferenceNames.map { name -> UserPreference(user, preferenceByName.getValue(name)) }
        userPreferenceRepository.saveAll(userPreferences)
    }

    fun updateUserPreferences(user: User, preferenceNames: List<String>, type: PreferenceType) {
        userPreferenceRepository.deleteByUserIdAndType(user.id, type)
        saveUserPreferences(user, preferenceNames, type)
    }

    fun updateUserPreference(user: User, preferenceName: String?, type: PreferenceType) {
        preferenceName?.let {
            val preference = requirePreferences(listOf(it), type).getValue(it)
            userPreferenceRepository.deleteByUserIdAndType(user.id, type)
            userPreferenceRepository.save(UserPreference(user, preference))
        }
    }

    // Single batch query instead of one findPreference(Name)?ByUserIdAndType call per preference type.
    private fun loadPreferences(userId: Long): UserPreferenceData =
        UserPreferenceData.from(userPreferenceRepository.findAllByUser_IdIn(listOf(userId)))
}
