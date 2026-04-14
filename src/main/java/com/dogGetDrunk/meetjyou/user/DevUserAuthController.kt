package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.user.dto.DevRegisterRequest
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@Profile("dev")
@RequestMapping("/api/v1/dev/auth")
class DevUserAuthController(
    private val devUserAuthService: DevUserAuthService,
) {
    /**
     * Creates a dev user (or re-authenticates if the email already exists) and returns JWT tokens.
     * Only available in the dev profile.
     */
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: DevRegisterRequest): ResponseEntity<TokenResponse> {
        val response = devUserAuthService.registerOrLogin(request)
        return ResponseEntity.created(URI.create("/${response.uuid}")).body(response)
    }

    /**
     * Issues fresh JWT tokens for an existing user identified by UUID.
     * Only available in the dev profile.
     */
    @PostMapping("/token")
    fun token(@RequestParam uuid: UUID): ResponseEntity<TokenResponse> {
        val response = devUserAuthService.getTokenForUser(uuid)
        return ResponseEntity.ok(response)
    }
}
