package com.dogGetDrunk.meetjyou.party.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class UpdatePartyRequest(
    val name: String? = null,
    val destination: String? = null,
    val joined: Int? = null,
    val max: Int? = null,

    @field:JsonProperty("itin_start")
    val itinStart: LocalDateTime? = null,

    @field:JsonProperty("itin_finish")
    val itinFinish: LocalDateTime? = null,

    @field:JsonProperty("img_url")
    val imgUrl: String? = null,

    @field:JsonProperty("thumb_img_url")
    val thumbImgUrl: String? = null
)
