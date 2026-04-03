package com.dogGetDrunk.meetjyou.config.property

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.websocket")
data class WebSocketProperties(
    val allowedOriginPatterns: List<String> = emptyList(),
)
