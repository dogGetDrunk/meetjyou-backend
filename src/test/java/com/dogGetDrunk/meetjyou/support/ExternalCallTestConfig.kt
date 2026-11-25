package com.dogGetDrunk.meetjyou.support

import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialToken
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.zaxxer.hikari.HikariDataSource
import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionSynchronizationManager
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

    @Bean
    @Primary
    fun fakeSocialVerifierRegistry(probe: VerifierProbe): SocialVerifierRegistry {
        val registry = mockk<SocialVerifierRegistry>()
        every { registry.get(any()) } answers {
            val provider = firstArg<AuthProvider>()
            object : SocialVerifier {
                override fun verifyAndExtract(token: SocialToken, nonce: String?) = probe.answer(provider)
            }
        }
        return registry
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
