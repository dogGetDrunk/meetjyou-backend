package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.config.property.LoadTestTokenProperties
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private const val MIN_SECRET_LENGTH = 32

@Component
class LoadTestTokenKillSwitch(
    private val props: LoadTestTokenProperties
) {
    private val log = LoggerFactory.getLogger(LoadTestTokenKillSwitch::class.java)

    @PostConstruct
    fun guard() {
        check(!(props.enabled && props.secret.length < MIN_SECRET_LENGTH)) {
            "SECURITY KILL SWITCH: load-test-token.enabled=true requires a secret of at least " +
                "$MIN_SECRET_LENGTH characters. Configured secret length=${props.secret.length}"
        }

        if (props.enabled) {
            log.warn("[LOAD-TEST-TOKEN] enabled=true. Synthetic token issuance is reachable at /internal/load-test-token.")
        }
    }
}
