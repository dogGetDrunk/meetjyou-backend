package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.party.Party
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdatePartyNameRequest(
    @field:Size(max = Party.NAME_MAX_LENGTH)
    @field:NotBlank
    val name: String,
)
