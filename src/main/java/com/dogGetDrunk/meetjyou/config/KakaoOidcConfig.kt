package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.auth.jwt.AudienceValidator
import com.dogGetDrunk.meetjyou.auth.social.kakao.KakaoJwkSource
import com.dogGetDrunk.meetjyou.auth.social.kakao.KakaoOidcProperties
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.web.client.RestClient

@Configuration
class KakaoOidcConfig(
    private val props: KakaoOidcProperties
) {

    @Bean
    @Qualifier("kakaoJwtDecoder")
    fun kakaoJwtDecoder(): JwtDecoder {
        val processor = DefaultJWTProcessor<SecurityContext>()
        processor.jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, KakaoJwkSource.create(props))
        // Spring validators below own claims validation, just as with the URI-based builder.
        processor.setJWTClaimsSetVerifier { _, _ -> }
        val decoder = NimbusJwtDecoder(processor)
        val validators: OAuth2TokenValidator<Jwt> = DelegatingOAuth2TokenValidator(
            JwtTimestampValidator(),
            JwtIssuerValidator(props.issuer),
            AudienceValidator(props.clientId)
        )
        decoder.setJwtValidator(validators)
        return decoder
    }

    // The builder bean is prototype-scoped, so these timeouts stay local to the Kakao REST calls.
    @Bean
    @Qualifier("kakaoRestClient")
    fun kakaoRestClient(restClientBuilder: RestClient.Builder): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(props.connectTimeout)
        requestFactory.setReadTimeout(props.readTimeout)
        return restClientBuilder.requestFactory(requestFactory).build()
    }
}
