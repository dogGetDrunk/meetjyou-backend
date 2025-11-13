package com.dogGetDrunk.meetjyou.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("oracle.oci")
data class OracleProps(
    val bucketName: String,
    val namespace: String,
    val parExpirationMinutes: Long,
    val auth: Auth = Auth()
) {
    data class Auth(
        val mode: Mode = Mode.FILE,
        val configFilePath: String? = null,
        val profile: String = "DEFAULT"
    )

    enum class Mode { FILE, INSTANCE }
}
