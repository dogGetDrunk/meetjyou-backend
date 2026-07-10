package com.dogGetDrunk.meetjyou.config.property

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "load-test-token")
data class LoadTestTokenProperties(
    val enabled: Boolean = false,
    val secret: String = "",
    val ttlMillis: Long = 300_000,
)
