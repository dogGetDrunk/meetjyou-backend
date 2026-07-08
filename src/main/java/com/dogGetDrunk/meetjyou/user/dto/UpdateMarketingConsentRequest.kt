package com.dogGetDrunk.meetjyou.user.dto

data class UpdateMarketingConsentRequest(
    val snsConsented: Boolean,
    val emailConsented: Boolean,
)
