package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PreferenceNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
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
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val preferenceRepository: PreferenceRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val termsService: TermsService,
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    private val log = LoggerFactory.getLogger(UserService::class.java)

    companion object {
        private const val PREFERENCE_NOT_FOUND = "Preference not found in DB: name={}, type={}"
    }

    @Transactional
    fun createUser(request: RegistrationRequest, principal: SocialPrincipal): User {
        val createdUser = userRepository.save(
            User(
                email = request.email,
                nickname = request.nickname,
                authProvider = request.authProvider,
                externalId = principal.subject,
            ).apply {
                bio = request.bio.normalizeOrNull()
            }
        )

        saveUserPreference(createdUser, request.gender.name, PreferenceType.GENDER)
        saveUserPreference(createdUser, request.age.name, PreferenceType.AGE)

        saveUserPreferences(createdUser, request.personalities.map { it.name }, PreferenceType.PERSONALITY)
        saveUserPreferences(createdUser, request.travelStyles.map { it.name }, PreferenceType.TRAVEL_STYLE)
        saveUserPreferences(createdUser, request.diet.map { it.name }, PreferenceType.DIET)
        saveUserPreferences(createdUser, request.etc.map { it.name }, PreferenceType.ETC)

        log.info("User saved successfully. uuid: {}, email: {}", createdUser.uuid, createdUser.email)

        return createdUser
    }

    @Transactional
    fun withdrawUser() {
        val uuid = currentUserProvider.uuid
        val user = userRepository.findByUuid(uuid) ?: throw UserNotFoundException(uuid)

        log.info("Processing user withdrawal (user uuid: {})", uuid)
        user.status = UserStatus.DELETED
        refreshTokenRepository.revokeAllByUser(user)
        log.info("User withdrawal completed (user uuid: {})", uuid)
    }

    @Transactional
    fun updateUser(request: UserUpdateRequest): BasicUserResponse {
        val user = currentUserProvider.user
        user.nickname = request.nickname
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

    @Transactional(readOnly = true)
    fun getPublicUserProfile(uuid: UUID): PublicUserResponse {
        val user = userRepository.findByUuid(uuid) ?: throw UserNotFoundException(uuid)
        return PublicUserResponse.of(user, loadPreferences(user.id))
    }

    @Transactional(readOnly = true)
    fun getAllUsersProfile(pageable: Pageable): Page<BasicUserResponse> {
        val users = userRepository.findAll(pageable)
        if (users.isEmpty) return users.map { BasicUserResponse.of(it, emptyList()) }
        val prefsMap = userPreferenceRepository.findAllByUser_IdIn(users.content.map { it.id })
            .groupBy { it.user.id }
        return users.map { BasicUserResponse.of(it, prefsMap[it.id] ?: emptyList()) }
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

    fun isDuplicateNickname(nickname: String): Boolean {
        return userRepository.existsByNickname(nickname)
    }

    fun saveUserPreference(user: User, preferenceName: String, type: PreferenceType) {
        preferenceRepository.findByNameAndType(preferenceName, type)?.let { preference ->
            userPreferenceRepository.save(UserPreference(user, preference))
        } ?: log.warn(PREFERENCE_NOT_FOUND, preferenceName, type)
    }

    fun saveUserPreferences(user: User, preferenceNames: List<String>, type: PreferenceType) {
        if (preferenceNames.isEmpty()) return
        val preferenceByName = preferenceRepository.findAllByTypeAndNameIn(type, preferenceNames)
            .associateBy { it.name }
        val userPreferences = preferenceNames.mapNotNull { name ->
            preferenceByName[name]?.let { UserPreference(user, it) }
                ?: run { log.warn(PREFERENCE_NOT_FOUND, name, type); null }
        }
        userPreferenceRepository.saveAll(userPreferences)
    }

    fun updateUserPreferences(user: User, preferenceNames: List<String>, type: PreferenceType) {
        userPreferenceRepository.deleteByUserIdAndType(user.id, type)
        saveUserPreferences(user, preferenceNames, type)
    }

    fun updateUserPreference(user: User, preferenceName: String?, type: PreferenceType) {
        preferenceName?.let {
            preferenceRepository.findByNameAndType(it, type)?.let { preference ->
                userPreferenceRepository.deleteByUserIdAndType(user.id, type)
                userPreferenceRepository.save(UserPreference(user, preference))
            } ?: log.warn(PREFERENCE_NOT_FOUND, it, type)
        }
    }

    // Single batch query instead of one findPreference(Name)?ByUserIdAndType call per preference type.
    private fun loadPreferences(userId: Long): UserPreferenceData {
        val userPrefs = userPreferenceRepository.findAllByUser_IdIn(listOf(userId))
        fun name(type: PreferenceType) = userPrefs
            .firstOrNull { it.preference.type == type }?.preference?.name
            ?: throw PreferenceNotFoundException(type.name)
        fun nameList(type: PreferenceType) = userPrefs
            .filter { it.preference.type == type }.map { it.preference.name }

        return UserPreferenceData(
            gender = name(PreferenceType.GENDER),
            age = name(PreferenceType.AGE),
            personalities = nameList(PreferenceType.PERSONALITY),
            travelStyles = nameList(PreferenceType.TRAVEL_STYLE),
            diet = nameList(PreferenceType.DIET),
            etc = nameList(PreferenceType.ETC),
        )
    }
}
