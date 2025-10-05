package com.dogGetDrunk.meetjyou.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("oracle.oci")
data class OracleProps(
    val auth: Auth = Auth()
) {
    data class Auth(
        val mode: Mode = Mode.FILE,
        val configFilePath: String? = null,
        val profile: String = "DEFAULT"
    )

    enum class Mode { FILE, INSTANCE }
}
