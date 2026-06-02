package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PreferenceNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import com.dogGetDrunk.meetjyou.image.DefaultProfileImageProvider
import com.dogGetDrunk.meetjyou.image.ImageTarget
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.UserPreference
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.user.dto.BasicUserResponse
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.dto.UserUpdateRequest
import com.dogGetDrunk.meetjyou.user.dto.normalizeOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val preferenceRepository: PreferenceRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val jwtProvider: JwtProvider,
    private val defaultProfileImageProvider: DefaultProfileImageProvider,
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

        request.personalities.forEach { saveUserPreference(createdUser, it.name, PreferenceType.PERSONALITY) }
        request.travelStyles.forEach { saveUserPreference(createdUser, it.name, PreferenceType.TRAVEL_STYLE) }
        request.diet.forEach { saveUserPreference(createdUser, it.name, PreferenceType.DIET) }
        request.etc.forEach { saveUserPreference(createdUser, it.name, PreferenceType.ETC) }

        log.info("User saved successfully. uuid: {}, email: {}", createdUser.uuid, createdUser.email)

        return createdUser
    }

    @Transactional
    fun withdrawUser() {
        val uuid = SecurityUtil.getCurrentUserUuid()

        if (!userRepository.existsByUuid(uuid)) {
            throw UserNotFoundException(uuid)
        }

        log.info("Processing user withdrawal (user uuid: {})", uuid.toString())
        if (userRepository.deleteByUuid(uuid) > 0) {
            log.info("User withdrawal completed (user uuid: {})", uuid.toString())
        } else {
            log.warn("User withdrawal completed with no rows deleted (user uuid: {})", uuid.toString())
        }
    }

    @Transactional
    fun updateUser(request: UserUpdateRequest): BasicUserResponse {
        val currentUserUuid = SecurityUtil.getCurrentUserUuid()
        val user = userRepository.findByUuid(currentUserUuid)
            ?.also {
                it.nickname = request.nickname
                it.bio = request.bio.normalizeOrNull()
            }
            ?: throw UserNotFoundException(currentUserUuid)

        updateUserPreference(user, request.gender.name, PreferenceType.GENDER)
        updateUserPreference(user, request.age.name, PreferenceType.AGE)
        updateUserPreferences(user, request.personalities.map { it.name }, PreferenceType.PERSONALITY)
        updateUserPreferences(user, request.travelStyles.map { it.name }, PreferenceType.TRAVEL_STYLE)
        updateUserPreferences(user, request.diet.map { it.name }, PreferenceType.DIET)
        updateUserPreferences(user, request.etc.map { it.name }, PreferenceType.ETC)

        userRepository.save(user)
        log.info("User profile updated. uuid={}", user.uuid)
        return getUserProfile(currentUserUuid)
    }

    @Transactional(readOnly = true)
    fun getUserProfile(uuid: UUID): BasicUserResponse {
        val user = userRepository.findByUuid(uuid)
            ?: throw UserNotFoundException(uuid)

        return toBasicUserResponse(user)
    }

    @Transactional(readOnly = true)
    fun getAllUsersProfile(): List<BasicUserResponse> {
        return userRepository.findAll().map { toBasicUserResponse(it) }
    }

    @Transactional
    fun confirmProfileImage() {
        val uuid = SecurityUtil.getCurrentUserUuid()
        val user = userRepository.findByUuid(uuid) ?: throw UserNotFoundException(uuid)
        user.imgUrl = ImageTarget.USER_PROFILE_ORIGINAL.toObjectName(uuid)
        user.thumbImgUrl = ImageTarget.USER_PROFILE_THUMBNAIL.toObjectName(uuid)
    }

    @Transactional
    fun clearProfileImage() {
        val uuid = SecurityUtil.getCurrentUserUuid()
        val user = userRepository.findByUuid(uuid) ?: throw UserNotFoundException(uuid)
        user.imgUrl = null
        user.thumbImgUrl = null
    }

    @Transactional
    fun updateMarketingConsent(consented: Boolean) {
        val uuid = SecurityUtil.getCurrentUserUuid()
        val user = userRepository.findByUuid(uuid) ?: throw UserNotFoundException(uuid)
        user.marketingConsented = consented
    }

    private fun toBasicUserResponse(user: User): BasicUserResponse {
        val thumbImgUrl = user.thumbImgUrl ?: defaultProfileImageProvider.getDefaultThumbnailUrl()
        return BasicUserResponse(
            uuid = user.uuid,
            nickname = user.nickname,
            bio = user.bio,
            thumbImgUrl = thumbImgUrl,
            gender = getPreferenceName(user.id, PreferenceType.GENDER),
            age = getPreferenceName(user.id, PreferenceType.AGE),
            personalities = getPreferenceNames(user.id, PreferenceType.PERSONALITY),
            travelStyles = getPreferenceNames(user.id, PreferenceType.TRAVEL_STYLE),
            diet = getPreferenceNames(user.id, PreferenceType.DIET),
            etc = getPreferenceNames(user.id, PreferenceType.ETC),
            authProvider = user.authProvider,
            marketingConsented = user.marketingConsented,
        )
    }

    fun isDuplicateNickname(nickname: String): Boolean {
        return userRepository.existsByNickname(nickname)
    }

    fun saveUserPreference(user: User, preferenceName: String, type: PreferenceType) {
        preferenceRepository.findByNameAndType(preferenceName, type)?.let { preference ->
            userPreferenceRepository.save(UserPreference(user, preference))
        } ?: log.warn(PREFERENCE_NOT_FOUND, preferenceName, type)
    }

    fun updateUserPreferences(user: User, preferenceNames: List<String>, type: PreferenceType) {
        userPreferenceRepository.deleteByUserIdAndType(user.id, type)
        preferenceNames.forEach { preferenceName ->
            preferenceRepository.findByNameAndType(preferenceName, type)?.let { preference ->
                userPreferenceRepository.save(UserPreference(user, preference))
            } ?: log.warn(PREFERENCE_NOT_FOUND, preferenceName, type)
        }
    }

    fun updateUserPreference(user: User, preferenceName: String?, type: PreferenceType) {
        preferenceName?.let {
            preferenceRepository.findByNameAndType(it, type)?.let { preference ->
                userPreferenceRepository.deleteByUserIdAndType(user.id, type)
                userPreferenceRepository.save(UserPreference(user, preference))
            } ?: log.warn(PREFERENCE_NOT_FOUND, it, type)
        }
    }

    fun getPreferenceName(userId: Long, type: PreferenceType): String {
        return userPreferenceRepository.findPreferenceByUserIdAndType(userId, type)?.name
            ?: throw PreferenceNotFoundException(type.name)
    }

    private fun getPreferenceNames(userId: Long, type: PreferenceType): List<String> {
        return userPreferenceRepository.findPreferencesByUserIdAndType(userId, type)
            .map { it.name }
    }
}
