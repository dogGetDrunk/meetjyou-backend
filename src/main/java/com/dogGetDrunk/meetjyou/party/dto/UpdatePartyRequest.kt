package com.dogGetDrunk.meetjyou.party.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

data class UpdatePartyRequest(
    val name: String? = null,
    val location: String? = null,
    val joined: Int? = null,
    val capacity: Int? = null,

    @field:JsonProperty("itin_start")
    val itinStart: LocalDate? = null,

    @field:JsonProperty("itin_finish")
    val itinFinish: LocalDate? = null,

    @field:JsonProperty("img_url")
    val imgUrl: String? = null,

    @field:JsonProperty("thumb_img_url")
    val thumbImgUrl: String? = null
)
