package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.exception.business.auth.LoadTestTokenForbiddenException
import com.dogGetDrunk.meetjyou.config.property.LoadTestTokenProperties
import com.dogGetDrunk.meetjyou.user.dto.LoadTestTokenResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant

private const val SYNTHETIC_EMAIL = "synthetic-loadtest@internal.meetjyou"
private const val SYNTHETIC_NICKNAME = "loadtest"
private const val SYNTHETIC_EXTERNAL_ID = "loadtest-synthetic"

/**
 * Issues short-lived access tokens for a single, fixed synthetic account so load tests can hit
 * authenticated endpoints in the release profile without a real OAuth login. Gated by a static
 * secret compared in constant time; see LoadTestTokenKillSwitch for the startup safety check.
 */
@Service
class LoadTestTokenService(
    private val props: LoadTestTokenProperties,
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
) {
    private val log = LoggerFactory.getLogger(LoadTestTokenService::class.java)

    @Transactional
    fun issueToken(providedSecret: String?): LoadTestTokenResponse {
        if (!props.enabled || !isSecretValid(providedSecret)) {
            log.warn("Load-test token request rejected.")
            throw LoadTestTokenForbiddenException()
        }

        val user = userRepository.findByEmail(SYNTHETIC_EMAIL)
            ?: userRepository.save(
                User(
                    email = SYNTHETIC_EMAIL,
                    nickname = SYNTHETIC_NICKNAME,
                    authProvider = AuthProvider.KAKAO,
                    externalId = SYNTHETIC_EXTERNAL_ID,
                )
            )

        val accessToken = jwtProvider.generateAccessToken(user.uuid, user.email, user.role, props.ttlMillis)
        log.warn("Load-test token issued. uuid={}", user.uuid)

        return LoadTestTokenResponse(accessToken, Instant.now().plusMillis(props.ttlMillis))
    }

    private fun isSecretValid(provided: String?): Boolean {
        if (provided.isNullOrEmpty() || props.secret.isEmpty()) return false
        return MessageDigest.isEqual(provided.toByteArray(), props.secret.toByteArray())
    }
}
