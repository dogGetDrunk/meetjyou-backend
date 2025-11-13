package com.dogGetDrunk.meetjyou.auth.jwt

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

class AudienceValidator(
    private val expectedAudience: String
) : OAuth2TokenValidator<Jwt> {

    private val error = OAuth2Error("invalid_audience", "Invalid audience", null)

    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val claim = token.audience
        return if (claim.contains(expectedAudience)) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(error)
        }
    }
}
