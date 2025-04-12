package com.dogGetDrunk.meetjyou.user.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class LoginRequest(
    @field:JsonProperty("uuid") private val uuidString: String,
    val email: String,
) {
    val uuid: UUID
        get() = UUID.fromString(uuidString)
}
