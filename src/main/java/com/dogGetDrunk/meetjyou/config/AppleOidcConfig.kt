package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.auth.jwt.AudienceValidator
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleOidcProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.time.Duration

@Configuration
class AppleOidcConfig(
    private val props: AppleOidcProperties,
) {

    @Bean
    @Qualifier("appleJwtDecoder")
    fun appleJwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder.withJwkSetUri(props.jwksUri).build()
        val validators: OAuth2TokenValidator<Jwt> = DelegatingOAuth2TokenValidator(
            JwtTimestampValidator(Duration.ofSeconds(props.clockSkewSeconds)),
            JwtIssuerValidator(props.issuer),
            AudienceValidator(props.clientId),
        )
        decoder.setJwtValidator(validators)
        return decoder
    }
}
