package com.dogGetDrunk.meetjyou.version

import java.time.Instant

data class AppVersionDto(
    val platform: Platform,
    val version: String,
    val forceUpdate: Boolean,
    val message: String? = null,
    val storeReleased: Boolean = false,
    val releasedAt: Instant,
) {
    companion object {
        fun fromEntity(appVersion: AppVersion) = AppVersionDto(
            platform = appVersion.platform,
            version = appVersion.version,
            forceUpdate = appVersion.forceUpdate,
            message = appVersion.message,
            storeReleased = appVersion.storeReleased,
            releasedAt = appVersion.releasedAt,
        )
    }
}
