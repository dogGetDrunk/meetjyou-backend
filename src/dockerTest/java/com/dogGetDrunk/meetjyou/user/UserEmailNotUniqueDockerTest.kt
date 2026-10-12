package com.dogGetDrunk.meetjyou.user

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.sql.DriverManager
import org.flywaydb.core.Flyway
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * A user is identified by (auth_provider, external_id), not by email, so the same email must be
 * allowed across providers. The UNIQUE constraint lives only in the MySQL schema (the entity does
 * not declare it and the H2 test schema is generated from the entity), so this needs real MySQL.
 */
class UserEmailNotUniqueDockerTest : BehaviorSpec({
    val mysql = MySQLContainer(DockerImageName.parse(MYSQL_IMAGE))

    beforeSpec { mysql.start() }
    afterSpec { mysql.stop() }

    Given("Flyway 마이그레이션을 실제 MySQL에 적용하고") {
        When("같은 이메일로 provider가 다른 유저 두 명을 저장하면") {
            Then("둘 다 저장된다") {
                Flyway.configure()
                    .dataSource(mysql.jdbcUrl, mysql.username, mysql.password)
                    .load()
                    .migrate()

                insertUser(mysql, uuid = KAKAO_USER_UUID, provider = "KAKAO", externalId = "kakao-1", nickname = "kakaoUser")
                insertUser(mysql, uuid = GOOGLE_USER_UUID, provider = "GOOGLE", externalId = "google-1", nickname = "googleUser")

                countUsersWithEmail(mysql) shouldBe 2
            }
        }
    }
})

private const val MYSQL_IMAGE = "mysql:8.0.41"
private const val SHARED_EMAIL = "same@example.com"
private const val KAKAO_USER_UUID = "11111111-1111-1111-1111-111111111111"
private const val GOOGLE_USER_UUID = "22222222-2222-2222-2222-222222222222"
private const val INSERT_USER =
    "INSERT INTO user (uuid, email, nickname, auth_provider, external_id, role) VALUES (?, ?, ?, ?, ?, 'USER')"
private const val COUNT_BY_EMAIL = "SELECT COUNT(*) FROM user WHERE email = ?"

private fun insertUser(mysql: MySQLContainer<*>, uuid: String, provider: String, externalId: String, nickname: String) {
    DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
        connection.prepareStatement(INSERT_USER).use { statement ->
            statement.setString(1, uuid)
            statement.setString(2, SHARED_EMAIL)
            statement.setString(3, nickname)
            statement.setString(4, provider)
            statement.setString(5, externalId)
            statement.executeUpdate()
        }
    }
}

private fun countUsersWithEmail(mysql: MySQLContainer<*>): Int =
    DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
        connection.prepareStatement(COUNT_BY_EMAIL).use { statement ->
            statement.setString(1, SHARED_EMAIL)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
    }
