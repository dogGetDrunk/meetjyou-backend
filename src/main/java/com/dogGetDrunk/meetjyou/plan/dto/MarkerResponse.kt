package com.dogGetDrunk.meetjyou.plan.dto

import com.dogGetDrunk.meetjyou.plan.Marker
import java.time.Instant
import java.util.UUID

data class MarkerResponse(
    val uuid: UUID,
    val lat: Double,
    val lng: Double,
    val date: Instant,
    val dayNum: Int,
    val idx: Int,
    val place: String,
    val memo: String?,
    val planUuid: UUID,
) {
    companion object {
        fun of(marker: Marker) = MarkerResponse(
            uuid = marker.uuid,
            lat = marker.lat,
            lng = marker.lng,
            date = marker.date,
            dayNum = marker.dayNum,
            idx = marker.idx,
            place = marker.place,
            memo = marker.memo,
            planUuid = marker.plan.uuid,
        )
    }
}
