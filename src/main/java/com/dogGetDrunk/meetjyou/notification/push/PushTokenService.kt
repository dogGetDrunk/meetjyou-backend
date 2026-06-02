package com.dogGetDrunk.meetjyou.notification.push

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.auth.UnauthenticatedException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PushTokenNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.VersionNotFoundException
import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import com.dogGetDrunk.meetjyou.notification.push.dto.PushTokenResponse
import com.dogGetDrunk.meetjyou.notification.push.dto.RegisterPushTokenRequest
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.version.AppVersionRepository
import com.dogGetDrunk.meetjyou.version.Platform
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PushTokenService(
    private val pushTokenRepository: PushTokenRepository,
    private val userRepository: UserRepository,
    private val appVersionRepository: AppVersionRepository,
) {
    private val log = LoggerFactory.getLogger(PushTokenService::class.java)

    @Transactional
    fun register(request: RegisterPushTokenRequest): PushTokenResponse {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        val user = userRepository.findByUuid(userUuid)
            ?: throw UserNotFoundException(userUuid)

        val appVersionPlatform = request.platformEnum.toAppVersionPlatform()
        val appVersion = appVersionRepository.findByVersionAndPlatform(request.appVersion, appVersionPlatform)
            ?: throw VersionNotFoundException(request.appVersion)

        val existing = pushTokenRepository.findByToken(request.token)
        val entity = if (existing != null) {
            existing.active = true
            existing
        } else {
            PushToken(
                token = request.token,
                platform = request.platformEnum,
                deviceModel = request.deviceModel,
                active = true,
                user = user,
                appVersion = appVersion,
            )
        }

        val saved = pushTokenRepository.save(entity)
        log.info("Registered push token: userUuid=$userUuid, platform=${saved.platform}, tokenUuid=${saved.uuid}")
        return PushTokenResponse.of(saved)
    }

    @Transactional
    fun deactivate(token: String) {
        val entity = pushTokenRepository.findByToken(token)
            ?: throw PushTokenNotFoundException(token)

        if (entity.user.uuid == SecurityUtil.getCurrentUserUuid()) {
            entity.active = false
        } else {
            throw UnauthenticatedException(SecurityUtil.getCurrentUserUuid().toString())
        }

        log.info("Deactivated push token: userUuid=${entity.user.uuid}, platform=${entity.platform}, tokenUuid=${entity.uuid}")
    }

    private fun PushToken.PushPlatform.toAppVersionPlatform(): Platform =
        when (this) {
            PushToken.PushPlatform.IOS -> Platform.IOS
            PushToken.PushPlatform.ANDROID -> Platform.ANDROID
            PushToken.PushPlatform.WEB -> throw InvalidInputException(
                errorCode = ErrorCode.INVALID_INPUT_VALUE,
                value = "WEB",
                message = "Web platform does not support app versioning",
            )
        }
}
