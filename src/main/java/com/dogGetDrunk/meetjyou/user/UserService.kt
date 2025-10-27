package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.UserPreference
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.user.dto.BasicUserResponse
import com.dogGetDrunk.meetjyou.user.dto.RefreshTokenRequest
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
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
) {
    private val log = LoggerFactory.getLogger(UserService::class.java)

    @Transactional
    fun createUser(request: RegistrationRequest, principal: SocialPrincipal): User {
        val createdUser = userRepository.save(
            User(
                email = request.email,
                nickname = request.nickname,
                birthDate = request.birthDate,
                authProvider = request.authProvider,
                externalId = principal.subject,
            ).apply {
                bio = request.bio.normalizeOrNull()
            }
        )

        // TODO: 소셜 로그인에서 받아오는 subject를 데이터베이스에 저장해야 할까?
        saveUserPreference(createdUser, request.gender.name)
        saveUserPreference(createdUser, request.age.name)

        request.personalities.forEach { saveUserPreference(createdUser, it.name) }
        request.travelStyles.forEach { saveUserPreference(createdUser, it.name) }
        request.diet.forEach { saveUserPreference(createdUser, it.name) }
        request.etc.forEach { saveUserPreference(createdUser, it.name) }

        log.info("User saved successfully. uuid: {}, email: {}", createdUser.uuid, createdUser.email)

        return createdUser
    }

    @Transactional
    fun withdrawUser(uuid: UUID, accessToken: String) {
        if (!userRepository.existsByUuid(uuid)) {
            throw UserNotFoundException(uuid)
        }

        jwtProvider.validateToken(accessToken)

        log.info("유저 탈퇴 시작 (user uuid: {})", uuid.toString())
        userRepository.deleteByUuid(uuid)
        log.info("유저 탈퇴 성공 (user uuid: {})", uuid.toString())
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
        log.info("유저 정보 수정 완료: uuid {}", user.uuid)
        return getUserProfile(currentUserUuid)
    }

    @Transactional(readOnly = true)
    fun getUserProfile(uuid: UUID): BasicUserResponse {
        val user = userRepository.findByUuid(uuid)
            ?: throw UserNotFoundException(uuid)

        return BasicUserResponse(
            uuid = user.uuid,
            nickname = user.nickname,
            bio = user.bio,
            gender = getPreferenceName(user.id, PreferenceType.GENDER),
            age = getPreferenceName(user.id, PreferenceType.AGE),
            personalities = getPreferenceNames(user.id, PreferenceType.PERSONALITY),
            travelStyles = getPreferenceNames(user.id, PreferenceType.TRAVEL_STYLE),
            diet = getPreferenceName(user.id, PreferenceType.DIET),
            etc = getPreferenceNames(user.id, PreferenceType.ETC),
            authProvider = user.authProvider,
        )
    }

    @Transactional(readOnly = true)
    fun getAllUsersProfile(): List<BasicUserResponse> {
        return userRepository.findAll().map { user ->
            BasicUserResponse(
                uuid = user.uuid,
                nickname = user.nickname,
                bio = user.bio,
                gender = getPreferenceName(user.id, PreferenceType.GENDER),
                age = getPreferenceName(user.id, PreferenceType.AGE),
                personalities = getPreferenceNames(user.id, PreferenceType.PERSONALITY),
                travelStyles = getPreferenceNames(user.id, PreferenceType.TRAVEL_STYLE),
                diet = getPreferenceName(user.id, PreferenceType.DIET),
                etc = getPreferenceNames(user.id, PreferenceType.ETC),
                authProvider = user.authProvider,
            )
        }
    }

    fun isDuplicateNickname(nickname: String): Boolean {
        return userRepository.existsByNickname(nickname)
    }

    fun saveUserPreference(user: User, preferenceName: String) {
        preferenceRepository.findByName(preferenceName)?.let { preference ->
            userPreferenceRepository.save(UserPreference(user, preference))
        }
    }

    fun updateUserPreferences(user: User, preferenceNames: List<String>, type: PreferenceType) {
        userPreferenceRepository.deleteByUserIdAndType(user.id, type)
        preferenceNames.forEach { preferenceName ->
            preferenceRepository.findByName(preferenceName)?.let { preference ->
                userPreferenceRepository.save(UserPreference(user, preference))
            }
        }
    }

    fun updateUserPreference(user: User, preferenceName: String?, type: PreferenceType) {
        preferenceName?.let {
            preferenceRepository.findByName(it)?.let { preference ->
                userPreferenceRepository.deleteByUserIdAndType(user.id, type)
                userPreferenceRepository.save(UserPreference(user, preference))
            }
        }
    }

    fun getPreferenceName(userId: Long, type: PreferenceType): String {
        return userPreferenceRepository.findPreferenceByUserIdAndType(userId, type)?.name
            ?: throw Exception("Preference not found") // TODO: 적절한 예외 만들기
    }

    private fun getPreferenceNames(userId: Long, type: PreferenceType): List<String> {
        return userPreferenceRepository.findPreferencesByUserIdAndType(userId, type)
            .map { it.name }
    }
}
