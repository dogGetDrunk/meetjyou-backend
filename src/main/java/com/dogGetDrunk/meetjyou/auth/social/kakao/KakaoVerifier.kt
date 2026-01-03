package com.dogGetDrunk.meetjyou.auth.social.kakao

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
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.concurrent.TimeUnit

@Component
class KakaoVerifier(
    private val props: KakaoOidcProperties,
    @Qualifier("kakaoJwtDecoder") private val kakaoJwtDecoder: JwtDecoder,
    private val meterRegistry: MeterRegistry,
) : SocialVerifier {

    private val log = LoggerFactory.getLogger(KakaoVerifier::class.java)

    override fun verifyAndExtract(token: SocialToken, nonce: String?): SocialPrincipal {
        // https://developers.kakao.com/docs/latest/ko/kakaologin/utilize#oidc-id-token-verify
        return when (token) {
            is IdToken -> verifyIdToken(token, nonce)
            is AccessToken -> verifyAccessToken(token)
        }
    }

    private fun verifyIdToken(token: IdToken, nonce: String? = null): SocialPrincipal {
        val sample = Timer.start(meterRegistry)
        val startedNs = System.nanoTime()

        return try {
            val verifiedJwt = kakaoJwtDecoder.decode(token.value)

            if (!nonce.isNullOrBlank() && verifiedJwt.claims["nonce"] != nonce) {
                throw InvalidJwtException()
            }

            SocialPrincipal(
                authProvider = AuthProvider.KAKAO,
                subject = verifiedJwt.subject,
                email = verifiedJwt.claims["email"] as? String ?: throw InvalidJwtException(),
            ).also {
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
                log.info("auth.verify.kakao.idToken success elapsedMs={}", elapsedMs)
            }
        } catch (e: Exception) {
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
            log.warn(
                "auth.verify.kakao.idToken fail elapsedMs={} ex={}",
                elapsedMs,
                e::class.java.simpleName,
            )
            throw e
        } finally {
            sample.stop(
                Timer.builder("auth.verify.duration")
                    .tag("provider", "kakao")
                    .tag("tokenType", "id_token")
                    .register(meterRegistry),
            )
        }
    }

    private fun verifyAccessToken(token: AccessToken): SocialPrincipal {
        val totalSample = Timer.start(meterRegistry)
        val totalStartedNs = System.nanoTime()

        try {
            val tokenInfo = measureExternalCallMs("kakao.tokenInfo") {
                RestClient.create(props.accessTokenInfoUri)
                    .get()
                    .headers {
                        it.setBearerAuth(token.value)
                        it.contentType = MediaType.APPLICATION_FORM_URLENCODED
                    }
                    .retrieve()
                    .toEntity(KakaoAccessTokenInfo::class.java)
                    .body ?: throw InvalidJwtException()
            }

            if (tokenInfo.appId.toString() != props.appId) {
                throw InvalidJwtException()
            }

            val userInfo = measureExternalCallMs("kakao.userInfo") {
                RestClient.create(props.userInfoUri)
                    .get()
                    .headers {
                        it.setBearerAuth(token.value)
                        it.contentType = MediaType.APPLICATION_FORM_URLENCODED
                    }
                    .retrieve()
                    .toEntity(KakaoUserInfo::class.java)
                    .body ?: throw InvalidJwtException()
            }

            val subject = tokenInfo.id.toString()
            val email = userInfo.kakaoAccount.email

            val totalElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - totalStartedNs)
            log.info("auth.verify.kakao.accessToken success elapsedMs={}", totalElapsedMs)

            return SocialPrincipal(
                authProvider = AuthProvider.KAKAO,
                subject = subject,
                email = email,
            )
        } catch (e: Exception) {
            val totalElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - totalStartedNs)
            log.warn(
                "auth.verify.kakao.accessToken fail elapsedMs={} ex={}",
                totalElapsedMs,
                e::class.java.simpleName,
            )
            throw e
        } finally {
            totalSample.stop(
                Timer.builder("auth.verify.duration")
                    .tag("provider", "kakao")
                    .tag("tokenType", "access_token")
                    .register(meterRegistry),
            )
        }
    }

    private fun <T> measureExternalCallMs(key: String, block: () -> T): T {
        val sample = Timer.start(meterRegistry)
        val startedNs = System.nanoTime()

        return try {
            block().also {
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
                log.info("auth.externalCall success key={} elapsedMs={}", key, elapsedMs)
            }
        } catch (e: Exception) {
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
            log.warn(
                "auth.externalCall fail key={} elapsedMs={} ex={}",
                key,
                elapsedMs,
                e::class.java.simpleName,
            )
            throw e
        } finally {
            sample.stop(
                Timer.builder("auth.external.duration")
                    .tag("provider", "kakao")
                    .tag("api", key)
                    .register(meterRegistry),
            )
        }
    }
}
