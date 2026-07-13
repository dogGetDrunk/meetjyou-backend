package com.dogGetDrunk.meetjyou.auth.social.apple

import com.dogGetDrunk.meetjyou.auth.social.AccessToken
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialToken
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.AuthProvider
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Component
class AppleVerifier(
    @Qualifier("appleJwtDecoder") private val appleJwtDecoder: JwtDecoder,
    private val meterRegistry: MeterRegistry,
) : SocialVerifier {

    private val log = LoggerFactory.getLogger(AppleVerifier::class.java)

    override fun verifyAndExtract(token: SocialToken, nonce: String?): SocialPrincipal {
        return when (token) {
            is IdToken -> verifyIdToken(token, nonce)
            is AccessToken -> throw InvalidJwtException(message = "Apple Access Token verification is not supported.")
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun verifyIdToken(token: IdToken, nonce: String?): SocialPrincipal {
        val sample = Timer.start(meterRegistry)
        val startedNs = System.nanoTime()

        return try {
            val verifiedJwt = appleJwtDecoder.decode(token.value)

            if (nonce.isNullOrBlank() || verifiedJwt.claims["nonce"] != sha256(nonce)) {
                throw InvalidJwtException()
            }

            SocialPrincipal(
                authProvider = AuthProvider.APPLE,
                subject = verifiedJwt.subject,
                email = verifiedJwt.claims["email"] as? String ?: throw InvalidJwtException(),
            ).also {
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
                log.info("auth.verify.apple.idToken success elapsedMs={}", elapsedMs)
            }
        } catch (e: Exception) {
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
            log.warn(
                "auth.verify.apple.idToken fail elapsedMs={} ex={}",
                elapsedMs,
                e::class.java.simpleName,
            )
            throw e
        } finally {
            sample.stop(
                Timer.builder("auth.verify.duration")
                    .tag("provider", "apple")
                    .tag("tokenType", "id_token")
                    .register(meterRegistry),
            )
        }
    }
}
