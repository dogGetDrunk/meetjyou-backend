package com.dogGetDrunk.meetjyou.support

import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialToken
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleVerifier
import com.dogGetDrunk.meetjyou.auth.social.google.GoogleVerifier
import com.dogGetDrunk.meetjyou.auth.social.kakao.KakaoOidcProperties
import com.dogGetDrunk.meetjyou.auth.social.kakao.KakaoVerifier
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.client.RestClient
import javax.sql.DataSource

/**
 * Shared by the external-call connection tests (SocialAuthConnectionIntegrationTest,
 * ImageParConnectionIntegrationTest, OpenInViewExclusionTest). They carry identical context annotations
 * so Spring caches one context for all three instead of one each.
 */
const val EXTERNAL_CALL_H2_URL =
    "jdbc:h2:mem:external_call_connection;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE"

@TestConfiguration
class ExternalCallTestConfig {
    @Bean
    fun verifierProbe(dataSource: DataSource) = VerifierProbe(dataSource.unwrap(HikariDataSource::class.java))

    // Each provider's verifier bean is replaced by a subclass that records its own provider, so the
    // production SocialVerifierRegistry still picks the verifier: a wrong dispatch records the wrong
    // provider. Subclasses, not mockks: unit tests call clearAllMocks(), which would wipe a mockk's
    // stubs inside this cached context.
    @Bean
    @Primary
    fun probeKakaoVerifier(
        probe: VerifierProbe,
        props: KakaoOidcProperties,
        @Qualifier("kakaoJwtDecoder") decoder: JwtDecoder,
        meterRegistry: MeterRegistry,
        @Qualifier("kakaoRestClient") restClient: RestClient,
    ): KakaoVerifier = object : KakaoVerifier(props, decoder, meterRegistry, restClient) {
        override fun verifyAndExtract(token: SocialToken, nonce: String?) = probe.answer(AuthProvider.KAKAO)
    }

    @Bean
    @Primary
    fun probeGoogleVerifier(
        probe: VerifierProbe,
        googleIdTokenVerifier: GoogleIdTokenVerifier,
        meterRegistry: MeterRegistry,
    ): GoogleVerifier = object : GoogleVerifier(googleIdTokenVerifier, meterRegistry) {
        override fun verifyAndExtract(token: SocialToken, nonce: String?) = probe.answer(AuthProvider.GOOGLE)
    }

    @Bean
    @Primary
    fun probeAppleVerifier(
        probe: VerifierProbe,
        @Qualifier("appleJwtDecoder") decoder: JwtDecoder,
        meterRegistry: MeterRegistry,
    ): AppleVerifier = object : AppleVerifier(decoder, meterRegistry) {
        override fun verifyAndExtract(token: SocialToken, nonce: String?) = probe.answer(AuthProvider.APPLE)
    }
}

/** Stands in for the provider verifiers and records what the request held during each call. */
class VerifierProbe(private val pool: HikariDataSource) {
    data class Observation(val provider: AuthProvider, val transactionActive: Boolean, val activeConnections: Int)

    val observations = mutableListOf<Observation>()
    var subject = ""

    fun answer(provider: AuthProvider): SocialPrincipal {
        observations += Observation(
            provider,
            TransactionSynchronizationManager.isActualTransactionActive(),
            pool.hikariPoolMXBean.activeConnections,
        )
        return SocialPrincipal(provider, subject, null)
    }
}
