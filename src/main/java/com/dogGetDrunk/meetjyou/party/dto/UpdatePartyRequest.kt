package com.dogGetDrunk.meetjyou.party.dto

import java.time.LocalDate

data class UpdatePartyRequest(
    val name: String,
    val location: String,
    val joined: Int,
    val capacity: Int,
    val itinStart: LocalDate,
    val itinFinish: LocalDate,
    val imgUrl: String,
    val thumbImgUrl: String,
)
