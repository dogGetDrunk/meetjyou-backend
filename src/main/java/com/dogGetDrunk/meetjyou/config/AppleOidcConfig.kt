package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.auth.social.apple.AppleJwksCache
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleJwtDecoder
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleOidcProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder

@Configuration
class AppleOidcConfig(
    private val props: AppleOidcProperties,
    private val appleJwksCache: AppleJwksCache,
) {

    @Bean
    @Qualifier("appleJwtDecoder")
    fun appleJwtDecoder(): JwtDecoder = AppleJwtDecoder(props, appleJwksCache)
}
