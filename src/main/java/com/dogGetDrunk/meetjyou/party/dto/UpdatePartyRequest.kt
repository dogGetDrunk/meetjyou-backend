package com.dogGetDrunk.meetjyou.party.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

data class UpdatePartyRequest(
    val name: String,
    val location: String,
    val joined: Int,
    val capacity: Int,

    @field:JsonProperty("itin_start")
    val itinStart: LocalDate,

    @field:JsonProperty("itin_finish")
    val itinFinish: LocalDate,

    @field:JsonProperty("img_url")
    val imgUrl: String,

    @field:JsonProperty("thumb_img_url")
    val thumbImgUrl: String,
)
