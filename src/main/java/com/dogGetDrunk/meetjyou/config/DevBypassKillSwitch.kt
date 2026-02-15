package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.config.property.DevBypassProperties
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

@Component
class DevBypassKillSwitch(
    private val env: Environment,
    private val props: DevBypassProperties
) {
    private val log = LoggerFactory.getLogger(DevBypassKillSwitch::class.java)

    @PostConstruct
    fun guard() {
        val isDevProfile = env.acceptsProfiles(Profiles.of("dev"))

        check(!(props.enabled && !isDevProfile)) {
            "SECURITY KILL SWITCH: dev.bypass.enabled=true is only allowed with 'dev' profile. " +
                    "Active profiles=${env.activeProfiles.joinToString(",")}"
        }

        if (props.enabled && isDevProfile) {
            log.warn("[DEV-BYPASS] enabled=true (dev profile). Certain endpoints will bypass auth as admin.")
        }
    }
}
