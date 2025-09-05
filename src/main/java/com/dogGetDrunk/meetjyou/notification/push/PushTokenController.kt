package com.dogGetDrunk.meetjyou.notification.push

import com.dogGetDrunk.meetjyou.notification.push.dto.PushTokenResponse
import com.dogGetDrunk.meetjyou.notification.push.dto.RegisterPushTokenRequest
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/push-tokens")
class PushTokenController(
    private val pushTokenService: PushTokenService,
) {

    @PostMapping
    fun register(@RequestBody request: RegisterPushTokenRequest): PushTokenResponse {
        return pushTokenService.register(request)
    }

    @DeleteMapping
    fun deactivate(@RequestParam token: String) {
        pushTokenService.deactivate(token)
    }
}
