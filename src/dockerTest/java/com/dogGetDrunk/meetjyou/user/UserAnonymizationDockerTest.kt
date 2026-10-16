package com.dogGetDrunk.meetjyou.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import org.flywaydb.core.Flyway
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * The purge job writes generated values into columns whose limits exist only in the MySQL schema
 * (`nickname VARCHAR(10) UNIQUE`, `status VARCHAR(10)`, `external_id UNIQUE`). The H2 test schema
 * comes from the entity with default lengths, so an overflow there would pass silently.
 */
class UserAnonymizationDockerTest : BehaviorSpec({
    val mysql = MySQLContainer(DockerImageName.parse(MYSQL_IMAGE))

    beforeSpec {
        mysql.start()
        Flyway.configure().dataSource(mysql.jdbcUrl, mysql.username, mysql.password).load().migrate()
    }
    afterSpec { mysql.stop() }

    Given("user.id가 INT 최댓값인 탈퇴 유저를") {
        When("purge 잡과 같은 값으로 익명화하면") {
            Then("strict 모드의 MySQL이 길이·UNIQUE 위반 없이 저장한다") {
                connect(mysql).use { connection ->
                    val uuid = UUID.randomUUID()
                    insertUser(connection, MAX_INT_ID, uuid, nickname = "maxuser", externalId = "kakao-max")

                    anonymize(connection, MAX_INT_ID, uuid)

                    readNicknameAndStatus(connection, MAX_INT_ID) shouldBe
                        (User.anonymizedNickname(MAX_INT_ID) to UserStatus.PURGED.name)
                }
            }
        }
    }

    Given("익명화된 유저의 원래 external_id로") {
        When("새 유저가 가입하면") {
            Then("레거시 UNIQUE(external_id)에 걸리지 않는다") {
                connect(mysql).use { connection ->
                    val uuid = UUID.randomUUID()
                    insertUser(connection, PURGED_ID, uuid, nickname = "olduser", externalId = REUSED_EXTERNAL_ID)
                    anonymize(connection, PURGED_ID, uuid)

                    insertUser(connection, RENEWED_ID, UUID.randomUUID(), nickname = "newuser", externalId = REUSED_EXTERNAL_ID)

                    countByExternalId(connection, REUSED_EXTERNAL_ID) shouldBe 1
                }
            }
        }
    }

    Given("V38 CHECK 제약(chk_user_withdrawn_at)이 걸린 user 테이블에") {
        When("withdrawn_at 없이 DELETED 유저를 저장하면") {
            Then("purge 대상에서 영영 빠질 행이므로 거부된다") {
                connect(mysql).use { connection ->
                    shouldThrow<SQLException> {
                        insertWithStatus(connection, NO_WITHDRAWN_AT_ID, nickname = "nowithdr", externalId = "kakao-nocheck", status = "DELETED")
                    }.message shouldContain "chk_user_withdrawn_at"
                }
            }
        }

        When("withdrawn_at 없이 NORMAL 유저를 저장하면") {
            Then("정상 유저는 탈퇴 시각이 없으므로 저장된다") {
                connect(mysql).use { connection ->
                    insertWithStatus(connection, NORMAL_ID, nickname = "normal", externalId = "kakao-normal", status = "NORMAL")

                    countByExternalId(connection, "kakao-normal") shouldBe 1
                }
            }
        }
    }
})

// Explicit id: the INT-max row above leaves AUTO_INCREMENT with no room.
private fun insertWithStatus(connection: Connection, id: Long, nickname: String, externalId: String, status: String) {
    connection.prepareStatement(INSERT_WITH_STATUS).use { statement ->
        statement.setLong(1, id)
        statement.setString(2, UUID.randomUUID().toString())
        statement.setString(3, nickname)
        statement.setString(4, externalId)
        statement.setString(5, status)
        statement.executeUpdate()
    }
}

private const val MYSQL_IMAGE = "mysql:8.0.41"
private const val MAX_INT_ID = Int.MAX_VALUE.toLong()
private const val PURGED_ID = 10L
private const val RENEWED_ID = 11L
private const val NO_WITHDRAWN_AT_ID = 20L
private const val NORMAL_ID = 21L
private const val REUSED_EXTERNAL_ID = "kakao-reused"

private val INSERT_USER = """
    INSERT INTO user (id, uuid, email, nickname, auth_provider, external_id, role, status, withdrawn_at)
    VALUES (?, ?, 'user@example.com', ?, 'KAKAO', ?, 'USER', 'DELETED', NOW())
""".trimIndent()

private val ANONYMIZE_USER = """
    UPDATE user SET email = '', nickname = ?, external_id = ?, bio = NULL, status = ?
    WHERE id = ?
""".trimIndent()

private val INSERT_WITH_STATUS = """
    INSERT INTO user (id, uuid, email, nickname, auth_provider, external_id, role, status)
    VALUES (?, ?, 'user@example.com', ?, 'KAKAO', ?, 'USER', ?)
""".trimIndent()

private const val SELECT_NICKNAME_STATUS = "SELECT nickname, status FROM user WHERE id = ?"
private const val COUNT_BY_EXTERNAL_ID = "SELECT COUNT(*) FROM user WHERE external_id = ?"

private fun connect(mysql: MySQLContainer<*>): Connection =
    DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password)

private fun insertUser(connection: Connection, id: Long, uuid: UUID, nickname: String, externalId: String) {
    connection.prepareStatement(INSERT_USER).use { statement ->
        statement.setLong(1, id)
        statement.setString(2, uuid.toString())
        statement.setString(3, nickname)
        statement.setString(4, externalId)
        statement.executeUpdate()
    }
}

private fun anonymize(connection: Connection, id: Long, uuid: UUID) {
    connection.prepareStatement(ANONYMIZE_USER).use { statement ->
        statement.setString(1, User.anonymizedNickname(id))
        statement.setString(2, User.anonymizedExternalId(uuid))
        statement.setString(3, UserStatus.PURGED.name)
        statement.setLong(4, id)
        statement.executeUpdate()
    }
}

private fun readNicknameAndStatus(connection: Connection, id: Long): Pair<String, String> =
    connection.prepareStatement(SELECT_NICKNAME_STATUS).use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getString(1) to rows.getString(2)
        }
    }

private fun countByExternalId(connection: Connection, externalId: String): Int =
    connection.prepareStatement(COUNT_BY_EXTERNAL_ID).use { statement ->
        statement.setString(1, externalId)
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getInt(1)
        }
    }
