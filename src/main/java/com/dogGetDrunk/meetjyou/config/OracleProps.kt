package com.dogGetDrunk.meetjyou.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("oracle.oci")
data class OracleProps(
    val bucketName: String,
    val namespace: String,
    val parExpirationMinutes: Long,
    val region: String,
    val auth: Auth = Auth()
) {
    val parBaseUrl: String
        get() = "https://objectstorage.${region}.oraclecloud.com"

    data class Auth(
        val mode: Mode = Mode.FILE,
        val configFilePath: String? = null,
        val profile: String = "DEFAULT"
    )

    enum class Mode { FILE, INSTANCE }
}
