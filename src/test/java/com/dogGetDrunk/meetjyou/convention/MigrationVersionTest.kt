package com.dogGetDrunk.meetjyou.convention

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.io.File

/**
 * Two branches can each add the same Flyway version under different file names. Git merges that
 * without a conflict and the H2 test profile runs with Flyway disabled, so only startup on MySQL fails.
 */
class MigrationVersionTest : BehaviorSpec({
    Given("Flyway 마이그레이션 파일") {
        val versions = File(MIGRATION_DIR).listFiles().orEmpty()
            .mapNotNull { file -> VERSIONED_MIGRATION.matchEntire(file.name)?.groupValues?.get(1) }

        Then("버전 번호가 중복되지 않는다") {
            versions.shouldNotBeEmpty()
            versions.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.shouldBeEmpty()
        }
    }
})

private const val MIGRATION_DIR = "src/main/resources/db/migration"
private val VERSIONED_MIGRATION = Regex("""V(\d+)__.+\.sql""")
