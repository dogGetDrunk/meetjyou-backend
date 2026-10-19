package com.dogGetDrunk.meetjyou.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import java.sql.Connection
import java.sql.DriverManager
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Issue #154: the DB VM is CPU-capped, so every extra round trip per request lowers the
 * throughput ceiling. Without the driver properties in application.yml, each Spring read-only
 * transaction also sends SET SESSION TRANSACTION READ ONLY/READ WRITE to MySQL. This runs the
 * same connection calls Spring makes around a read-only transaction and checks what MySQL saw.
 */
class JdbcSessionStateRoundTripDockerTest : BehaviorSpec({
    val mysql = MySQLContainer(DockerImageName.parse(MYSQL_IMAGE))
    lateinit var dataSource: HikariDataSource

    beforeSpec {
        mysql.start()
        dataSource = HikariDataSource(buildHikariConfig(mysql))
    }
    afterSpec {
        dataSource.close()
        mysql.stop()
    }

    Given("application.yml의 드라이버 속성을 적용한 커넥션 풀에서") {
        When("Spring처럼 read-only 트랜잭션을 열고 닫으면") {
            Then("MySQL에 세션 read-only 전환 문장이 가지 않는다") {
                dataSource.connection.use { connection ->
                    resetStatementDigests(mysql)
                    runReadOnlyTransaction(connection)
                }
                val statements = statementDigestsSinceReset(mysql)

                statements shouldContain PROBE_QUERY
                statements shouldNotContain READ_ONLY_SWITCH
                statements.filter { it.contains(READ_ONLY_PROBE) }.shouldBeEmpty()
            }
        }
    }
})

private const val MYSQL_IMAGE = "mysql:8.0.41"
private const val APPLICATION_YAML = "application.yml"
private const val DRIVER_PROPERTY_PREFIX = "spring.datasource.hikari.data-source-properties."
private const val PROBE_QUERY = "SELECT ?"
private const val PROBE_SQL = "SELECT 42"
private const val READ_ONLY_SWITCH = "SET SESSION TRANSACTION READ ONLY"
private const val RESET_DIGESTS = "TRUNCATE performance_schema.events_statements_summary_by_digest"
private const val READ_ONLY_PROBE = "transaction_read_only"
private const val ROOT_USER = "root"
// Excludes the observer's own TRUNCATE and this SELECT.
private val SELECT_DIGESTS = """
    SELECT DIGEST_TEXT
    FROM performance_schema.events_statements_summary_by_digest
    WHERE DIGEST_TEXT NOT LIKE 'TRUNCATE%' AND DIGEST_TEXT NOT LIKE '%events_statements_summary_by_digest%'
""".trimIndent()

private fun buildHikariConfig(mysql: MySQLContainer<*>): HikariConfig {
    val config = HikariConfig()
    config.jdbcUrl = mysql.jdbcUrl
    config.username = mysql.username
    config.password = mysql.password
    config.maximumPoolSize = 1
    loadDriverProperties().forEach { (key, value) -> config.addDataSourceProperty(key, value) }
    return config
}

// Reads the real application.yml so removing the properties there fails this test.
private fun loadDriverProperties(): Map<String, String> {
    val yaml = YamlPropertiesFactoryBean()
    yaml.setResources(ClassPathResource(APPLICATION_YAML))
    val properties = yaml.getObject() ?: return emptyMap()
    // Values keep their YAML types (Boolean), so stringPropertyNames() would skip them.
    return properties.keys.map { it.toString() }
        .filter { it.startsWith(DRIVER_PROPERTY_PREFIX) }
        .associate { it.removePrefix(DRIVER_PROPERTY_PREFIX) to properties[it].toString() }
}

// Mirrors DataSourceUtils.prepareConnectionForTransaction / resetConnectionAfterTransaction.
private fun runReadOnlyTransaction(connection: Connection) {
    connection.isReadOnly = true
    connection.autoCommit = false
    connection.createStatement().use { it.executeQuery(PROBE_SQL).close() }
    connection.commit()
    connection.autoCommit = true
    connection.isReadOnly = false
}

private fun resetStatementDigests(mysql: MySQLContainer<*>) =
    observe(mysql) { observer -> observer.createStatement().use { it.execute(RESET_DIGESTS) } }

private fun statementDigestsSinceReset(mysql: MySQLContainer<*>): List<String> =
    observe(mysql) { observer ->
        observer.createStatement().use { statement ->
            statement.executeQuery(SELECT_DIGESTS).use { rows ->
                generateSequence { if (rows.next()) rows.getString(1) else null }.toList()
            }
        }
    }

// performance_schema needs more than the container's default per-database user.
private fun <T> observe(mysql: MySQLContainer<*>, block: (Connection) -> T): T =
    DriverManager.getConnection(mysql.jdbcUrl, ROOT_USER, mysql.password).use(block)
