package com.dogGetDrunk.meetjyou.config

import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("dev")
@Configuration
class DevFlywayConfig {

    @Bean
    fun flywayMigrationStrategy(): FlywayMigrationStrategy = FlywayMigrationStrategy { flyway: Flyway ->
        flyway.repair()
        flyway.migrate()
    }
}
