package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.auth.dev.DevBypassAuthFilter
import com.dogGetDrunk.meetjyou.config.property.DevBypassProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("dev")
@Configuration
class DevBypassConfig {

    @Bean
    @ConditionalOnProperty(
        prefix = "dev.bypass",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false
    )
    fun devBypassAuthFilter(props: DevBypassProperties): DevBypassAuthFilter {
        return DevBypassAuthFilter(props)
    }
}
