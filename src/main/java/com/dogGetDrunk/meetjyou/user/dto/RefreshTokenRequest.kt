package com.dogGetDrunk.meetjyou.user.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class RefreshTokenRequest(
    @field:JsonProperty("uuid") private val uuidString: String
) {
    val uuid: UUID
        get() = UUID.fromString(uuidString)
}
