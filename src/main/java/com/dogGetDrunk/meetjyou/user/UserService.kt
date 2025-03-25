package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateEmailException
import com.dogGetDrunk.meetjyou.common.exception.business.UserNotFoundException
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
            throw DuplicateEmailException(request.email)
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

        val accessToken = jwtManager.generateAccessToken(createdUser.id)
        val refreshToken = jwtManager.generateRefreshToken(createdUser.id)

        return TokenResponse(createdUser.id, accessToken, refreshToken)
    }

    fun login(loginRequestA: LoginRequest): TokenResponse {
        val userId = loginRequestA.userId

        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException(userId)
        }

        val accessToken = jwtManager.generateAccessToken(userId)
        val refreshToken = jwtManager.generateRefreshToken(userId)

        return TokenResponse(userId, accessToken, refreshToken)
    }

    fun isDuplicateNickname(nickname: String): Boolean {
        return userRepository.existsByNickname(nickname)
    }

    fun refreshToken(refreshToken: String, userId: Long): TokenResponse {
        jwtManager.validateToken(refreshToken, userId)

        val newAccessToken = jwtManager.generateAccessToken(userId)
        val newRefreshToken = jwtManager.generateRefreshToken(userId)

        return TokenResponse(userId, newAccessToken, newRefreshToken)
    }

    @Transactional
    fun withdrawUser(userId: Long, accessToken: String) {
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException(userId)
        }

        jwtManager.validateToken(accessToken, userId)

        log.info("유저 탈퇴 시작 (user id: {})", userId)
        userRepository.deleteById(userId)
        log.info("유저 탈퇴 성공 (user id: {})", userId)
    }

    @Transactional
    fun updateUser(userId: Long, requestDto: UserUpdateRequest): BasicUserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }

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
        log.info("유저 정보 수정 완료: id {}", user.id)
        return getUserProfile(userId)
    }

    @Transactional(readOnly = true)
    fun getUserProfile(userId: Long): BasicUserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }

        return BasicUserResponse(
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
