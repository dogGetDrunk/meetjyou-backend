package com.dogGetDrunk.meetjyou.notification.sender

data class SendResult(
    val ok: Boolean,
    val permanent: Boolean = false,
    // True only when the provider reports the token itself as gone (not a payload problem),
    // so the token can be deactivated without risking healthy devices.
    val tokenInvalid: Boolean = false,
    val messageId: String? = null,
    val error: String? = null,
)
