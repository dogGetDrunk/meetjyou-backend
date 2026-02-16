package com.dogGetDrunk.meetjyou.config.property

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "dev.bypass")
data class DevBypassProperties(
    val enabled: Boolean = false,
)
