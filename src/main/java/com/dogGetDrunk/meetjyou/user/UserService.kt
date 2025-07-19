package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.common.exception.business.duplicate.UserAlreadyExistsException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.jwt.JwtManager
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.UserPreference
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.user.dto.BasicUserResponse
import com.dogGetDrunk.meetjyou.user.dto.LoginRequest
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
import com.dogGetDrunk.meetjyou.user.dto.UserUpdateRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val preferenceRepository: PreferenceRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val jwtManager: JwtManager,
) {
    private val log = LoggerFactory.getLogger(UserService::class.java)

    @Transactional
    fun createUser(request: RegistrationRequest): TokenResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw UserAlreadyExistsException(request.email)
        }

        val createdUser = userRepository.save(
            User(
                email = request.email,
                nickname = request.nickname,
                bio = request.bio,
                birthDate = request.birthDate,
                authProvider = request.authProvider,
            )
        )

        saveUserPreference(createdUser, request.gender.name)
        saveUserPreference(createdUser, request.age.name)

        request.personalities.forEach { saveUserPreference(createdUser, it.name) }
        request.travelStyles.forEach { saveUserPreference(createdUser, it.name) }
        saveUserPreference(createdUser, request.diet.name)
        request.etc.forEach { saveUserPreference(createdUser, it.name) }

        val accessToken = jwtManager.generateAccessToken(createdUser.uuid)
        val refreshToken = jwtManager.generateRefreshToken(createdUser.uuid)

        return TokenResponse(createdUser.uuid, accessToken, refreshToken)
    }

    fun login(request: LoginRequest): TokenResponse {
        // TODO: Email 검증 추가
        if (!userRepository.existsByUuid(request.uuid)) {
            throw UserNotFoundException(request.uuid)
        }

        val accessToken = jwtManager.generateAccessToken(request.uuid)
        val refreshToken = jwtManager.generateRefreshToken(request.uuid)

        return TokenResponse(request.uuid, accessToken, refreshToken)
    }

    fun isDuplicateNickname(nickname: String): Boolean {
        return userRepository.existsByNickname(nickname)
    }

    fun refreshToken(refreshToken: String, uuid: UUID): TokenResponse {
        jwtManager.validateToken(refreshToken, uuid)

        val newAccessToken = jwtManager.generateAccessToken(uuid)
        val newRefreshToken = jwtManager.generateRefreshToken(uuid)

        return TokenResponse(uuid, newAccessToken, newRefreshToken)
    }

    @Transactional
    fun withdrawUser(uuid: UUID, accessToken: String) {
        if (!userRepository.existsByUuid(uuid)) {
            throw UserNotFoundException(uuid)
        }

        jwtManager.validateToken(accessToken, uuid)

        log.info("유저 탈퇴 시작 (user uuid: {})", uuid.toString())
        userRepository.deleteByUuid(uuid)
        log.info("유저 탈퇴 성공 (user uuid: {})", uuid.toString())
    }

    @Transactional
    fun updateUser(uuid: UUID, requestDto: UserUpdateRequest): BasicUserResponse {
        val user = userRepository.findByUuid(uuid)
            ?: throw UserNotFoundException(uuid)
        user.apply {
            nickname = requestDto.nickname
            bio = requestDto.bio
        }

        updateUserPreference(user, requestDto.gender.name, 0)
        updateUserPreference(user, requestDto.age.name, 1)

        updateUserPreferences(user, requestDto.personalities.map { it.name }, 2)
        updateUserPreferences(user, requestDto.travelStyles.map { it.name }, 3)
        updateUserPreference(user, requestDto.diet.name, 4)
        updateUserPreferences(user, requestDto.etc.map { it.name }, 5)

        userRepository.save(user)
        log.info("유저 정보 수정 완료: uuid {}", user.uuid)
        return getUserProfile(uuid)
    }

    @Transactional(readOnly = true)
    fun getUserProfile(uuid: UUID): BasicUserResponse {
        val user = userRepository.findByUuid(uuid)
            ?: throw UserNotFoundException(uuid)

        return BasicUserResponse(
            uuid = user.uuid,
            nickname = user.nickname,
            bio = user.bio,
            gender = getPreferenceName(user.id, 0),
            age = getPreferenceName(user.id, 1),
            personalities = getPreferenceNames(user.id, 2),
            travelStyles = getPreferenceNames(user.id, 3),
            diet = getPreferenceName(user.id, 4),
            etc = getPreferenceNames(user.id, 5),
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
                gender = getPreferenceName(user.id, 0),
                age = getPreferenceName(user.id, 1),
                personalities = getPreferenceNames(user.id, 2),
                travelStyles = getPreferenceNames(user.id, 3),
                diet = getPreferenceName(user.id, 4),
                etc = getPreferenceNames(user.id, 5),
                authProvider = user.authProvider,
            )
        }
    }

    private fun saveUserPreference(user: User, preferenceName: String) {
        preferenceRepository.findByName(preferenceName)?.let { preference ->
            userPreferenceRepository.save(UserPreference(user, preference))
        }
    }

    private fun updateUserPreferences(user: User, preferenceNames: List<String>, type: Int) {
        userPreferenceRepository.deleteByUserIdAndType(user.id, type)
        preferenceNames.forEach { preferenceName ->
            preferenceRepository.findByName(preferenceName)?.let { preference ->
                userPreferenceRepository.save(UserPreference(user, preference))
            }
        }
    }

    private fun updateUserPreference(user: User, preferenceName: String?, type: Int) {
        preferenceName?.let {
            preferenceRepository.findByName(it)?.let { preference ->
                userPreferenceRepository.deleteByUserIdAndType(user.id, type)
                userPreferenceRepository.save(UserPreference(user, preference))
            }
        }
    }

    private fun getPreferenceName(userId: Long, type: Int): String {
        return userPreferenceRepository.findPreferenceByUserIdAndType(userId, type)?.name
            ?: throw Exception("Preference not found") // TODO: 적절한 예외 만들기
    }

    private fun getPreferenceNames(userId: Long, type: Int): List<String> {
        return userPreferenceRepository.findPreferencesByUserIdAndType(userId, type)
            .map { it.name }
    }
}
