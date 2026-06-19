package com.dogGetDrunk.meetjyou.config.property

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "admin")
data class AdminProperties(
    val claimPassphrase: String,
)
