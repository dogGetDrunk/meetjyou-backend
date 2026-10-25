package com.dogGetDrunk.meetjyou.auth.social.kakao

import com.dogGetDrunk.meetjyou.auth.social.AccessToken
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialToken
import com.dogGetDrunk.meetjyou.auth.social.SocialTokenRejection
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.ExternalServiceException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.AuthProvider
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.util.concurrent.TimeUnit

@Component
class KakaoVerifier(
    private val props: KakaoOidcProperties,
    @Qualifier("kakaoJwtDecoder") private val kakaoJwtDecoder: JwtDecoder,
    private val meterRegistry: MeterRegistry,
    restClientBuilder: RestClient.Builder,
) : SocialVerifier {

    private val log = LoggerFactory.getLogger(KakaoVerifier::class.java)

    // Injected builder instead of RestClient.create() so tests can bind a MockRestServiceServer.
    private val restClient: RestClient = restClientBuilder.build()

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
            val verifiedJwt = decodeIdToken(token.value)

            if (nonce.isNullOrBlank() || verifiedJwt.claims["nonce"] != nonce) {
                throw InvalidJwtException(message = SocialTokenRejection.NONCE_MISMATCH)
            }

            SocialPrincipal(
                authProvider = AuthProvider.KAKAO,
                subject = verifiedJwt.subject,
                // Present only when the user consented to share it; email is optional.
                email = verifiedJwt.claims["email"] as? String,
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
                getKakaoApi(props.accessTokenInfoUri, token, KakaoAccessTokenInfo::class.java)
            } ?: throw InvalidJwtException()

            if (tokenInfo.appId.toString() != props.appId) {
                throw InvalidJwtException()
            }

            val userInfo = measureExternalCallMs("kakao.userInfo") {
                getKakaoApi(props.userInfoUri, token, KakaoUserInfo::class.java)
            } ?: throw InvalidJwtException()

            val subject = tokenInfo.id.toString()
            val email = userInfo.kakaoAccount?.verifiedEmail()

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

    /**
     * A rejected id_token (expired, bad signature, failed claims) is BadJwtException, the client's
     * fault. Any other JwtException means Kakao's JWK set could not be used, a provider outage.
     */
    private fun decodeIdToken(idToken: String): Jwt = try {
        kakaoJwtDecoder.decode(idToken)
    } catch (e: BadJwtException) {
        throw e
    } catch (e: JwtException) {
        throw ExternalServiceException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE, KAKAO, e)
    }

    // 4xx is a bad or expired user token (401), except 429: our app key hit Kakao's quota.
    private fun translateKakaoError(request: HttpRequest, response: ClientHttpResponse) {
        val status = response.statusCode
        if (status.is4xxClientError && status != HttpStatus.TOO_MANY_REQUESTS) {
            throw InvalidJwtException(
                message = "Kakao rejected the access token: ${status.value()} ${request.uri.path}",
            )
        }
        throw ExternalServiceException(
            ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE,
            KAKAO,
            IllegalStateException("Kakao responded ${status.value()} for ${request.uri.path}"),
        )
    }

    // ResourceAccessException = Kakao unreachable (timeout, connection refused): an outage, not a bad token.
    private fun <T : Any> getKakaoApi(uri: String, token: AccessToken, type: Class<T>): T? = try {
        restClient.get()
            .uri(uri)
            .headers {
                it.setBearerAuth(token.value)
                it.contentType = MediaType.APPLICATION_FORM_URLENCODED
            }
            .retrieve()
            .onStatus(HttpStatusCode::isError, ::translateKakaoError)
            .toEntity(type)
            .body
    } catch (e: ResourceAccessException) {
        throw ExternalServiceException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE, KAKAO, e)
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

    private companion object {
        const val KAKAO = "kakao"
    }
}
