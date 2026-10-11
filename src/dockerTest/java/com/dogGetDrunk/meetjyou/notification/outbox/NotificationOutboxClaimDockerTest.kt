package com.dogGetDrunk.meetjyou.notification.outbox

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import org.flywaydb.core.Flyway
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Guards the outbox claim query against a real MySQL (issue #150). The query relies on
 * `FOR UPDATE SKIP LOCKED` and `NOW()`, and the test profile runs on H2 with Flyway disabled,
 * so neither the lease semantics nor the V35 column can be checked there.
 *
 * Runs the same SQL constant the repository uses, so the two cannot drift apart.
 */
class NotificationOutboxClaimDockerTest : BehaviorSpec({
    val mysql = MySQLContainer(DockerImageName.parse(MYSQL_IMAGE))

    beforeSpec {
        mysql.start()
        Flyway.configure().dataSource(mysql.jdbcUrl, mysql.username, mysql.password).load().migrate()
    }
    afterSpec { mysql.stop() }

    Given("상태와 available_at이 서로 다른 outbox 행들이 있을 때") {
        When("claim 쿼리를 실행하면") {
            Then("대기 시각이 지난 PENDING과 lease가 만료된 SENDING만 집는다") {
                connect(mysql).use { connection ->
                    insertRow(connection, DUE_PENDING, "PENDING", PAST)
                    insertRow(connection, FUTURE_PENDING, "PENDING", FUTURE)
                    insertRow(connection, EXPIRED_SENDING, "SENDING", PAST)
                    insertRow(connection, LEASED_SENDING, "SENDING", FUTURE)
                    insertRow(connection, DUE_SENT, "SENT", PAST)

                    claimDedupKeys(connection) shouldBe setOf(DUE_PENDING, EXPIRED_SENDING)
                }
            }
        }
    }

    Given("대기 시각이 지난 PENDING 행이 있을 때") {
        When("claim 표시 쿼리를 실행하면") {
            Then("SENDING으로 바뀌고 시도 횟수가 1 늘며 lease 동안 claim 쿼리에서 제외된다") {
                connect(mysql).use { connection ->
                    insertRow(connection, RECLAIMED, "PENDING", PAST)

                    markClaimed(connection, RECLAIMED)

                    readString(connection, SELECT_STATUS, RECLAIMED) shouldBe "SENDING"
                    readLong(connection, SELECT_ATTEMPTS, RECLAIMED) shouldBe 1
                    readLong(connection, SELECT_LEASE_REMAINING, RECLAIMED) shouldBeInRange LEASE_REMAINING_RANGE
                    claimDedupKeys(connection).contains(RECLAIMED) shouldBe false
                }
            }
        }
    }

    Given("V35 마이그레이션이 적용된 notification_outbox에") {
        When("delivered_token_ids에 JSON 배열 문자열을 저장하면") {
            Then("JSON 배열로 읽힌다") {
                connect(mysql).use { connection ->
                    insertRow(connection, WITH_DELIVERED, "SENT", PAST)
                    connection.prepareStatement(UPDATE_DELIVERED).use { statement ->
                        statement.setString(1, "[1,2]")
                        statement.setString(2, WITH_DELIVERED)
                        statement.executeUpdate()
                    }

                    readDeliveredLength(connection, WITH_DELIVERED) shouldBe 2
                }
            }
        }
    }
})

private const val MYSQL_IMAGE = "mysql:8.0.41"
private const val PAST = "NOW() - INTERVAL 1 MINUTE"
private const val FUTURE = "NOW() + INTERVAL 10 MINUTE"
private const val DUE_PENDING = "due-pending"
private const val FUTURE_PENDING = "future-pending"
private const val EXPIRED_SENDING = "expired-sending"
private const val LEASED_SENDING = "leased-sending"
private const val DUE_SENT = "due-sent"
private const val WITH_DELIVERED = "with-delivered"
private const val RECLAIMED = "reclaimed"
private const val LEASE_SECONDS = 600L
private const val SELECT_ID = "SELECT id FROM notification_outbox WHERE dedup_key = ?"
private const val SELECT_ATTEMPTS = "SELECT attempts FROM notification_outbox WHERE dedup_key = ?"
private const val SELECT_STATUS = "SELECT status FROM notification_outbox WHERE dedup_key = ?"
private const val SELECT_LEASE_REMAINING =
    "SELECT TIMESTAMPDIFF(SECOND, NOW(), available_at) FROM notification_outbox WHERE dedup_key = ?"

// The lease is set and read a moment apart, so allow a few seconds of slack below the full lease.
private val LEASE_REMAINING_RANGE = (LEASE_SECONDS - 10)..LEASE_SECONDS
private const val CLAIM_LIMIT = 100
private const val DEDUP_KEY_COLUMN = "dedup_key"
private const val UPDATE_DELIVERED = "UPDATE notification_outbox SET delivered_token_ids = ? WHERE dedup_key = ?"
private const val SELECT_DELIVERED_LENGTH =
    "SELECT JSON_LENGTH(delivered_token_ids) FROM notification_outbox WHERE dedup_key = ?"

private fun connect(mysql: MySQLContainer<*>): Connection {
    val connection = DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password)
    // Rows reference a user that does not exist; only the outbox columns matter here.
    connection.createStatement().use { it.execute("SET FOREIGN_KEY_CHECKS = 0") }
    return connection
}

private fun insertRow(connection: Connection, dedupKey: String, status: String, availableAtSql: String) {
    val sql = """
        INSERT INTO notification_outbox (uuid, type, data_json, dedup_key, status, available_at, user_id)
        VALUES (?, 'CHAT_MESSAGE', '{}', ?, ?, $availableAtSql, 1)
    """
    connection.prepareStatement(sql).use { statement ->
        statement.setString(1, UUID.randomUUID().toString())
        statement.setString(2, dedupKey)
        statement.setString(3, status)
        statement.executeUpdate()
    }
}

private fun claimDedupKeys(connection: Connection): Set<String> {
    val claimed = mutableSetOf<String>()
    connection.autoCommit = false
    connection.prepareStatement(LOCK_NEXT_CLAIMABLE_SQL.replace(":limit", "?")).use { statement ->
        statement.setInt(1, CLAIM_LIMIT)
        statement.executeQuery().use { rows ->
            while (rows.next()) {
                claimed.add(rows.getString(DEDUP_KEY_COLUMN))
            }
        }
    }
    connection.rollback()
    connection.autoCommit = true
    return claimed
}

private fun readDeliveredLength(connection: Connection, dedupKey: String): Int {
    connection.prepareStatement(SELECT_DELIVERED_LENGTH).use { statement ->
        statement.setString(1, dedupKey)
        statement.executeQuery().use { rows ->
            rows.next()
            return rows.getInt(1)
        }
    }
}

private fun markClaimed(connection: Connection, dedupKey: String) {
    val id = readLong(connection, SELECT_ID, dedupKey)
    val sql = MARK_CLAIMED_SQL.replace(":leaseSeconds", "?").replace("(:ids)", "(?)")
    connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, LEASE_SECONDS)
        statement.setLong(2, id)
        statement.executeUpdate()
    }
}

private fun readString(connection: Connection, sql: String, dedupKey: String): String {
    connection.prepareStatement(sql).use { statement ->
        statement.setString(1, dedupKey)
        statement.executeQuery().use { rows ->
            rows.next()
            return rows.getString(1)
        }
    }
}

private fun readLong(connection: Connection, sql: String, dedupKey: String): Long {
    connection.prepareStatement(sql).use { statement ->
        statement.setString(1, dedupKey)
        statement.executeQuery().use { rows ->
            rows.next()
            return rows.getLong(1)
        }
    }
}
