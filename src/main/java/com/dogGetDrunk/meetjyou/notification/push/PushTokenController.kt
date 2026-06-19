package com.dogGetDrunk.meetjyou.notification.push

import com.dogGetDrunk.meetjyou.notification.push.dto.PushTokenResponse
import com.dogGetDrunk.meetjyou.notification.push.dto.RegisterPushTokenRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import com.dogGetDrunk.meetjyou.config.RestControllerV1
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@RestControllerV1
@RequestMapping("/push-tokens")
class PushTokenController(
    private val pushTokenService: PushTokenService,
) {

    @PostMapping
    fun register(@Valid @RequestBody request: RegisterPushTokenRequest): PushTokenResponse {
        return pushTokenService.register(request)
    }

    @DeleteMapping
    fun deactivate(@RequestParam token: String) {
        pushTokenService.deactivate(token)
    }
}
