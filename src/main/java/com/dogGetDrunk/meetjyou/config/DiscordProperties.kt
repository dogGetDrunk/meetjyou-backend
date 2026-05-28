package com.dogGetDrunk.meetjyou.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("discord")
data class DiscordProperties(
    val webhookUrl: String = ""
)
