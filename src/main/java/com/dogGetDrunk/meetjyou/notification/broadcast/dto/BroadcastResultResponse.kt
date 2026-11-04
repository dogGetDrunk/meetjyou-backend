package com.dogGetDrunk.meetjyou.notification.broadcast.dto

data class BroadcastResultResponse(
    val recipientCount: Int,
) {
    companion object {
        fun of(count: Int): BroadcastResultResponse = BroadcastResultResponse(recipientCount = count)
    }
}
