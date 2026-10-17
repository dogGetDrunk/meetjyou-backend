package com.dogGetDrunk.meetjyou.user

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import org.flywaydb.core.Flyway
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * An account may sign up without an email, so V39 drops NOT NULL from both email columns. The H2
 * test schema is generated from the entities and never runs Flyway: a missing migration would pass
 * every H2 test and fail the first email-less sign-up in production.
 */
class UserEmailNullableDockerTest : BehaviorSpec({
    val mysql = MySQLContainer(DockerImageName.parse(MYSQL_IMAGE))

    beforeSpec {
        mysql.start()
        migrate(mysql, target = VERSION_BEFORE_NULLABLE_EMAIL)
        connect(mysql).use { connection -> insertPurgedUserWithBlankEmail(connection) }
        migrate(mysql, target = VERSION_NULLABLE_EMAIL)
    }
    afterSpec { mysql.stop() }

    Given("V39 이후 user·user_social_account 테이블에") {
        When("email 없는 유저와 소셜 계정을 저장하면") {
            Then("NOT NULL 위반 없이 저장된다") {
                connect(mysql).use { connection ->
                    insertEmaillessUserWithSocialAccount(connection)

                    readUserEmail(connection, EMAILLESS_USER_ID) shouldBe null
                    countSocialAccountsWithNullEmail(connection, EMAILLESS_USER_ID) shouldBe 1
                }
            }
        }
    }

    Given("V39 이전에 익명화되어 email이 빈 문자열인 유저는") {
        When("V39가 적용되면") {
            Then("다른 email 없는 행과 같게 NULL로 바뀐다") {
                connect(mysql).use { connection ->
                    readUserEmail(connection, PURGED_USER_ID) shouldBe null
                }
            }
        }
    }
})

private const val MYSQL_IMAGE = "mysql:8.0.41"
private const val VERSION_BEFORE_NULLABLE_EMAIL = "38"
private const val VERSION_NULLABLE_EMAIL = "39"
private const val PURGED_USER_ID = 10L
private const val EMAILLESS_USER_ID = 11L

private val INSERT_PURGED_USER = """
    INSERT INTO user (id, uuid, email, nickname, auth_provider, external_id, role, status, withdrawn_at)
    VALUES (?, ?, '', '~a', 'KAKAO', ?, 'USER', 'PURGED', NOW())
""".trimIndent()

private val INSERT_EMAILLESS_USER = """
    INSERT INTO user (id, uuid, email, nickname, auth_provider, external_id, role, status)
    VALUES (?, ?, NULL, 'noemail', 'KAKAO', 'kakao-no-email', 'USER', 'NORMAL')
""".trimIndent()

private val INSERT_EMAILLESS_SOCIAL_ACCOUNT = """
    INSERT INTO user_social_account (user_id, provider, external_id, email)
    VALUES (?, 'KAKAO', 'kakao-no-email', NULL)
""".trimIndent()

private const val SELECT_USER_EMAIL = "SELECT email FROM user WHERE id = ?"
private const val COUNT_NULL_EMAIL_SOCIAL_ACCOUNTS =
    "SELECT COUNT(*) FROM user_social_account WHERE user_id = ? AND email IS NULL"

private fun migrate(mysql: MySQLContainer<*>, target: String) {
    Flyway.configure()
        .dataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        .target(target)
        .load()
        .migrate()
}

private fun connect(mysql: MySQLContainer<*>): Connection =
    DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password)

private fun insertPurgedUserWithBlankEmail(connection: Connection) {
    val uuid = UUID.randomUUID()
    connection.prepareStatement(INSERT_PURGED_USER).use { statement ->
        statement.setLong(1, PURGED_USER_ID)
        statement.setString(2, uuid.toString())
        statement.setString(3, User.anonymizedExternalId(uuid))
        statement.executeUpdate()
    }
}

private fun insertEmaillessUserWithSocialAccount(connection: Connection) {
    connection.prepareStatement(INSERT_EMAILLESS_USER).use { statement ->
        statement.setLong(1, EMAILLESS_USER_ID)
        statement.setString(2, UUID.randomUUID().toString())
        statement.executeUpdate()
    }
    connection.prepareStatement(INSERT_EMAILLESS_SOCIAL_ACCOUNT).use { statement ->
        statement.setLong(1, EMAILLESS_USER_ID)
        statement.executeUpdate()
    }
}

private fun readUserEmail(connection: Connection, id: Long): String? =
    connection.prepareStatement(SELECT_USER_EMAIL).use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getString(1)
        }
    }

private fun countSocialAccountsWithNullEmail(connection: Connection, userId: Long): Int =
    connection.prepareStatement(COUNT_NULL_EMAIL_SOCIAL_ACCOUNTS).use { statement ->
        statement.setLong(1, userId)
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getInt(1)
        }
    }
