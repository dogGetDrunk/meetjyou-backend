package com.dogGetDrunk.meetjyou.notification.sender

data class SendResult(
    val ok: Boolean,
    val permanent: Boolean = false,
    val messageId: String? = null,
    val error: String? = null,
)
