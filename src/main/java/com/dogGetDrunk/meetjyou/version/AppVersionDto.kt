package com.dogGetDrunk.meetjyou.version

import java.time.LocalDateTime

data class AppVersionDto(
    val version: String,
    val forceUpdate: Boolean,
    val downloadUrl: String,
    val releasedAt: LocalDateTime
) {
    companion object {
        fun fromEntity(appVersion: AppVersion): AppVersionDto {
            return AppVersionDto(
                version = appVersion.version,
                forceUpdate = appVersion.forceUpdate,
                downloadUrl = appVersion.downloadUrl,
                releasedAt = appVersion.releasedAt
            )
        }
    }
}
