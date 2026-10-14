package com.dogGetDrunk.meetjyou.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.sql.DriverManager
import java.sql.SQLIntegrityConstraintViolationException
import org.flywaydb.core.Flyway
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * V37 moves the social identity into user_social_account. The backfill and the unique keys exist
 * only in the MySQL schema (the H2 test schema is generated from entities and never runs Flyway),
 * so both need real MySQL.
 */
class UserSocialAccountMigrationDockerTest : BehaviorSpec({
    val mysql = MySQLContainer(DockerImageName.parse(MYSQL_IMAGE))

    beforeSpec {
        mysql.start()
        migrate(mysql, target = VERSION_BEFORE_SOCIAL_ACCOUNT)
        insertUser(mysql, uuid = KAKAO_USER_UUID, provider = "KAKAO", externalId = "kakao-1", nickname = "kakaoUser")
        insertUser(mysql, uuid = GOOGLE_USER_UUID, provider = "GOOGLE", externalId = "google-1", nickname = "googleUser")
        migrate(mysql, target = VERSION_SOCIAL_ACCOUNT)
    }
    afterSpec { mysql.stop() }

    Given("V37 이전에 가입한 유저가 있는 DB에") {
        When("V37 마이그레이션을 적용하면") {
            Then("유저마다 provider·external_id·email이 같은 소셜 계정 행이 하나씩 생긴다") {
                socialAccountsOf(mysql, KAKAO_USER_UUID) shouldBe listOf("KAKAO|kakao-1|$KAKAO_USER_UUID@example.com")
                socialAccountsOf(mysql, GOOGLE_USER_UUID) shouldBe listOf("GOOGLE|google-1|$GOOGLE_USER_UUID@example.com")
            }
        }
    }

    Given("소셜 계정 테이블에") {
        When("external_id는 같고 provider가 다른 행을 넣으면") {
            Then("저장된다") {
                insertSocialAccount(mysql, userUuid = GOOGLE_USER_UUID, provider = "APPLE", externalId = "kakao-1")

                socialAccountsOf(mysql, GOOGLE_USER_UUID).size shouldBe 2
            }
        }

        When("이미 다른 유저에게 연결된 (provider, external_id)를 넣으면") {
            Then("유니크 제약으로 거부된다") {
                shouldThrow<SQLIntegrityConstraintViolationException> {
                    insertSocialAccount(mysql, userUuid = GOOGLE_USER_UUID, provider = "KAKAO", externalId = "kakao-1")
                }
            }
        }

        When("한 유저에게 같은 provider의 계정을 하나 더 넣으면") {
            Then("유니크 제약으로 거부된다") {
                shouldThrow<SQLIntegrityConstraintViolationException> {
                    insertSocialAccount(mysql, userUuid = KAKAO_USER_UUID, provider = "KAKAO", externalId = "kakao-2")
                }
            }
        }
    }
})

private const val MYSQL_IMAGE = "mysql:8.0.41"
private const val VERSION_BEFORE_SOCIAL_ACCOUNT = "36"
private const val VERSION_SOCIAL_ACCOUNT = "37"
private const val KAKAO_USER_UUID = "11111111-1111-1111-1111-111111111111"
private const val GOOGLE_USER_UUID = "22222222-2222-2222-2222-222222222222"
private const val INSERT_USER =
    "INSERT INTO user (uuid, email, nickname, auth_provider, external_id, role) VALUES (?, ?, ?, ?, ?, 'USER')"
private val INSERT_SOCIAL_ACCOUNT = """
    INSERT INTO user_social_account (user_id, provider, external_id, email)
    SELECT id, ?, ?, email FROM user WHERE uuid = ?
""".trimIndent()
private val SELECT_SOCIAL_ACCOUNTS = """
    SELECT a.provider, a.external_id, a.email
    FROM user_social_account a JOIN user u ON u.id = a.user_id
    WHERE u.uuid = ?
    ORDER BY a.id
""".trimIndent()

private fun migrate(mysql: MySQLContainer<*>, target: String) {
    Flyway.configure()
        .dataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        .target(target)
        .load()
        .migrate()
}

private fun insertUser(mysql: MySQLContainer<*>, uuid: String, provider: String, externalId: String, nickname: String) {
    DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
        connection.prepareStatement(INSERT_USER).use { statement ->
            statement.setString(1, uuid)
            statement.setString(2, "$uuid@example.com")
            statement.setString(3, nickname)
            statement.setString(4, provider)
            statement.setString(5, externalId)
            statement.executeUpdate()
        }
    }
}

private fun insertSocialAccount(mysql: MySQLContainer<*>, userUuid: String, provider: String, externalId: String) {
    DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
        connection.prepareStatement(INSERT_SOCIAL_ACCOUNT).use { statement ->
            statement.setString(1, provider)
            statement.setString(2, externalId)
            statement.setString(3, userUuid)
            statement.executeUpdate()
        }
    }
}

private fun socialAccountsOf(mysql: MySQLContainer<*>, userUuid: String): List<String> =
    DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password).use { connection ->
        connection.prepareStatement(SELECT_SOCIAL_ACCOUNTS).use { statement ->
            statement.setString(1, userUuid)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add("${rows.getString(1)}|${rows.getString(2)}|${rows.getString(3)}")
                    }
                }
            }
        }
    }
