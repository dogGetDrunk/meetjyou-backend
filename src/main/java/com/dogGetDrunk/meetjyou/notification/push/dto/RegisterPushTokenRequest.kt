package com.dogGetDrunk.meetjyou.notification.push.dto

import com.dogGetDrunk.meetjyou.notification.push.PushToken
import jakarta.validation.constraints.NotBlank

data class RegisterPushTokenRequest(
    @field:NotBlank
    val token: String,
    @field:NotBlank
    val platform: String,
    @field:NotBlank
    val appVersion: String,
    @field:NotBlank
    val deviceModel: String,
) {
    val platformEnum: PushToken.PushPlatform
        get() = PushToken.PushPlatform.valueOf(platform.uppercase())
}
