package com.dogGetDrunk.meetjyou.terms

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import org.flywaydb.core.Flyway
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Guards #224: user_terms is an append-only consent history (one row per change, latest id wins),
 * so the same (user, terms, action) must be insertable again — agree -> withdraw -> agree used to
 * hit a unique key and fail with 500. The constraint lives only in the Flyway schema (the H2 test
 * schema is generated from the entity), so this needs real MySQL.
 */
class UserTermsConsentHistoryDockerTest : BehaviorSpec({
    val mysql = MySQLContainer(DockerImageName.parse(MYSQL_IMAGE))

    beforeSpec { mysql.start() }
    afterSpec { mysql.stop() }

    Given("Flyway 마이그레이션을 실제 MySQL에 적용하고") {
        When("같은 약관 버전에 동의 → 철회 → 재동의 이력을 차례로 저장하면") {
            Then("세 행이 모두 저장되고 최신 행은 동의 상태다") {
                Flyway.configure()
                    .dataSource(mysql.jdbcUrl, mysql.username, mysql.password)
                    .load()
                    .migrate()

                DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
                    val userId = insertUser(connection)
                    val termsId = insertMarketingTerms(connection)
                    CONSENT_ROUND_TRIP.forEach { action -> insertUserTerms(connection, userId, termsId, action) }

                    countUserTerms(connection, userId, termsId) shouldBe CONSENT_ROUND_TRIP.size
                    latestAction(connection, userId, termsId) shouldBe AGREED
                }
            }
        }
    }
})

private const val MYSQL_IMAGE = "mysql:8.0.41"
private const val AGREED = "AGREED"
private const val WITHDRAWN = "WITHDRAWN"
private val CONSENT_ROUND_TRIP = listOf(AGREED, WITHDRAWN, AGREED)

private const val INSERT_USER = """
    INSERT INTO user (uuid, email, nickname, auth_provider, external_id, role)
    VALUES ('11111111-1111-1111-1111-111111111111', 'consent@example.com', 'consenter', 'KAKAO', 'kakao-1', 'USER')
"""
private const val INSERT_TERMS = """
    INSERT INTO terms (uuid, type, version, display_text, required, content_object_key, content_hash, effective_at)
    VALUES ('22222222-2222-2222-2222-222222222222', 'MARKETING_SNS_EVENTS', '1.0', 'Marketing', FALSE,
            'terms/marketing-1.0.html', REPEAT('a', 64), CURRENT_TIMESTAMP)
"""
private const val INSERT_USER_TERMS = "INSERT INTO user_terms (user_id, terms_id, action) VALUES (?, ?, ?)"
private const val COUNT_USER_TERMS = "SELECT COUNT(*) FROM user_terms WHERE user_id = ? AND terms_id = ?"
private const val LATEST_ACTION =
    "SELECT action FROM user_terms WHERE user_id = ? AND terms_id = ? ORDER BY id DESC LIMIT 1"

private fun insertUser(connection: Connection): Long = insertReturningId(connection, INSERT_USER)

private fun insertMarketingTerms(connection: Connection): Long = insertReturningId(connection, INSERT_TERMS)

private fun insertReturningId(connection: Connection, sql: String): Long =
    connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { statement ->
        statement.executeUpdate()
        statement.generatedKeys.use { keys ->
            keys.next()
            keys.getLong(1)
        }
    }

private fun insertUserTerms(connection: Connection, userId: Long, termsId: Long, action: String) {
    connection.prepareStatement(INSERT_USER_TERMS).use { statement ->
        statement.setLong(1, userId)
        statement.setLong(2, termsId)
        statement.setString(3, action)
        statement.executeUpdate()
    }
}

private fun countUserTerms(connection: Connection, userId: Long, termsId: Long): Int =
    connection.prepareStatement(COUNT_USER_TERMS).use { statement ->
        statement.setLong(1, userId)
        statement.setLong(2, termsId)
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getInt(1)
        }
    }

private fun latestAction(connection: Connection, userId: Long, termsId: Long): String =
    connection.prepareStatement(LATEST_ACTION).use { statement ->
        statement.setLong(1, userId)
        statement.setLong(2, termsId)
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getString(1)
        }
    }
