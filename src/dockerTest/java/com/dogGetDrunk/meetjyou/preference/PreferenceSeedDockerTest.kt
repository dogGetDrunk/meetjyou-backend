package com.dogGetDrunk.meetjyou.preference

import com.dogGetDrunk.meetjyou.support.MySqlDatabase
import com.dogGetDrunk.meetjyou.support.SharedMySql
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.sql.DriverManager
import org.flywaydb.core.Flyway

/**
 * Guards the invariant that the preference enums and the Flyway seed data stay in sync
 * (issue #131). The seed migrations use MySQL-only syntax and the test profile runs on H2 with
 * Flyway disabled, so this check needs a real MySQL instance.
 *
 * Runs in the `dockerTest` Gradle task, not in `test`, because it needs a Docker daemon.
 */
class PreferenceSeedDockerTest : BehaviorSpec({
    val mysql = SharedMySql.newDatabase()

    Given("Flyway 마이그레이션을 실제 MySQL에 적용하면") {
        When("preference 테이블의 시드 데이터를 읽으면") {
            Then("타입별 값 집합이 Kotlin enum과 정확히 일치한다") {
                migrate(mysql)

                readSeededPreferences(mysql) shouldBe expectedPreferences()
            }
        }
    }
})

private const val TYPE_COLUMN = "type"
private const val NAME_COLUMN = "name"
private const val SELECT_PREFERENCES = "SELECT type, name FROM preference"

private fun migrate(mysql: MySqlDatabase) {
    Flyway.configure()
        .dataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        .load()
        .migrate()
}

private fun expectedPreferences(): Map<String, Set<String>> = mapOf(
    PreferenceType.GENDER.name to Gender.entries.map { it.name }.toSet(),
    PreferenceType.AGE.name to Age.entries.map { it.name }.toSet(),
    PreferenceType.PERSONALITY.name to Personality.entries.map { it.name }.toSet(),
    PreferenceType.TRAVEL_STYLE.name to TravelStyle.entries.map { it.name }.toSet(),
    PreferenceType.DIET.name to Diet.entries.map { it.name }.toSet(),
    PreferenceType.ETC.name to Etc.entries.map { it.name }.toSet(),
)

private fun readSeededPreferences(mysql: MySqlDatabase): Map<String, Set<String>> {
    val seeded = mutableMapOf<String, MutableSet<String>>()
    DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(SELECT_PREFERENCES).use { rows ->
                while (rows.next()) {
                    val names = seeded.getOrPut(rows.getString(TYPE_COLUMN)) { mutableSetOf() }
                    names.add(rows.getString(NAME_COLUMN))
                }
            }
        }
    }
    return seeded
}
