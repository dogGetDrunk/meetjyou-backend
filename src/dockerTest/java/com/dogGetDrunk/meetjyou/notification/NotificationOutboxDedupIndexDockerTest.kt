package com.dogGetDrunk.meetjyou.notification

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.sql.DriverManager
import org.flywaydb.core.Flyway
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

class NotificationOutboxDedupIndexDockerTest : BehaviorSpec({
    val mysql = MySQLContainer(DockerImageName.parse(MYSQL_IMAGE))

    beforeSpec { mysql.start() }
    afterSpec { mysql.stop() }

    Given("AT-004-1: an independent MySQL database migrated with Flyway") {
        When("V43 is applied") {
            Then("the dedup key index exists and is non-unique") {
                Flyway.configure().dataSource(mysql.jdbcUrl, mysql.username, mysql.password).load().migrate()
                DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery(V43_HISTORY_SQL).use { rows ->
                            rows.next() shouldBe true
                            rows.getBoolean("success") shouldBe true
                            rows.next() shouldBe false
                        }
                        statement.executeQuery(INDEX_SQL).use { rows ->
                            rows.next() shouldBe true
                            rows.getString("INDEX_NAME") shouldBe INDEX_NAME
                            rows.getString("COLUMN_NAME") shouldBe "dedup_key"
                            rows.getInt("NON_UNIQUE") shouldBe 1
                            rows.next() shouldBe false
                        }
                    }
                }
            }
        }
    }
})

private const val MYSQL_IMAGE = "mysql:8.0.41"
private const val INDEX_NAME = "idx_notification_outbox_dedup_key"
private const val V43_HISTORY_SQL = "SELECT success FROM flyway_schema_history WHERE version = '43'"
private const val INDEX_SQL = """
    SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'notification_outbox'
      AND index_name = 'idx_notification_outbox_dedup_key'
    ORDER BY SEQ_IN_INDEX
"""
