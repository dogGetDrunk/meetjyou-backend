package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.auth.social.apple.AppleAuthClient
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleClientSecretFactory
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleJwksCache
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleJwtDecoder
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleOidcProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class AppleOidcConfig(
    private val props: AppleOidcProperties,
) {

    @Bean
    fun appleJwksCache(): AppleJwksCache = AppleJwksCache.create(props)

    @Bean
    @Qualifier("appleJwtDecoder")
    fun appleJwtDecoder(appleJwksCache: AppleJwksCache): JwtDecoder = AppleJwtDecoder(props, appleJwksCache)

    // Explicit timeouts: these calls run inside the withdrawal request, so a hung Apple endpoint
    // must not hold it open. The builder bean is prototype-scoped, so configuring it here is local.
    @Bean
    fun appleAuthClient(restClientBuilder: RestClient.Builder): AppleAuthClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(CONNECT_TIMEOUT)
            setReadTimeout(READ_TIMEOUT)
        }
        val restClient = restClientBuilder.requestFactory(requestFactory).build()
        return AppleAuthClient(props, AppleClientSecretFactory(props), restClient)
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
