package com.dogGetDrunk.meetjyou.notification.push.dto

import com.dogGetDrunk.meetjyou.notification.push.PushToken

data class RegisterPushTokenRequest(
    val token: String,
    val platform: String,
    val appVersion: String,
    val deviceModel: String,
) {
    val platformEnum: PushToken.PushPlatform
        get() = PushToken.PushPlatform.valueOf(platform.uppercase())
}
