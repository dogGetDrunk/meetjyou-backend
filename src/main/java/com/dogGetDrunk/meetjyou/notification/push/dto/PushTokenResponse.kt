package com.dogGetDrunk.meetjyou.notification.push.dto

import com.dogGetDrunk.meetjyou.notification.push.PushToken
import java.util.UUID

data class PushTokenResponse(
    val uuid: UUID,
    val token: String,
    val platform: String,
    val active: Boolean,
) {
    companion object {
        fun of(entity: PushToken): PushTokenResponse = PushTokenResponse(
            uuid = entity.uuid,
            token = entity.token,
            platform = entity.platform.name,
            active = entity.active,
        )
    }
}
