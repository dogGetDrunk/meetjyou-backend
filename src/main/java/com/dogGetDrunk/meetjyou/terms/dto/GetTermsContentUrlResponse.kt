package com.dogGetDrunk.meetjyou.terms.dto

import java.time.Instant

data class GetTermsContentUrlResponse(
    val termsUuid: String,
    val downloadUrl: String,
    val httpMethod: String,
    val expiresAt: Instant,
)
