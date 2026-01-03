package com.dogGetDrunk.meetjyou.auth.social.google

import com.dogGetDrunk.meetjyou.auth.social.AccessToken
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialToken
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class GoogleVerifier(
    private val googleIdTokenVerifier: GoogleIdTokenVerifier,
    private val meterRegistry: MeterRegistry,
) : SocialVerifier {

    private val log = LoggerFactory.getLogger(GoogleVerifier::class.java)

    override fun verifyAndExtract(token: SocialToken, nonce: String?): SocialPrincipal {
        return when (token) {
            is IdToken -> verifyIdToken(token)
            is AccessToken -> throw InvalidJwtException(message = "Google Access Token verification is not supported.")
        }
    }

    fun verifyIdToken(token: IdToken): SocialPrincipal {
        val sample = Timer.start(meterRegistry)
        val startedNs = System.nanoTime()

        return try {
            val verifiedJwt = googleIdTokenVerifier.verify(token.value)
                ?: throw InvalidJwtException()

            val payload = verifiedJwt.payload
            val subject = payload.subject ?: throw InvalidJwtException()
            val email = payload.email ?: throw InvalidJwtException()

            SocialPrincipal(
                authProvider = AuthProvider.GOOGLE,
                subject = subject,
                email = email,
            ).also {
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
                log.info("auth.verify.google.idToken success elapsedMs={}", elapsedMs)
            }
        } catch (e: Exception) {
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
            log.warn(
                "auth.verify.google.idToken fail elapsedMs={} ex={}",
                elapsedMs,
                e::class.java.simpleName,
            )
            throw e
        } finally {
            sample.stop(
                Timer.builder("auth.verify.duration")
                    .tag("provider", "google")
                    .tag("tokenType", "id_token")
                    .register(meterRegistry),
            )
        }
    }
}
