package com.dogGetDrunk.meetjyou.version.dto

data class VersionCheckResponse(
    val updateRequired: Boolean,
    val updateAvailable: Boolean,
    val latestVersion: String?,
    val downloadUrl: String?,
)
