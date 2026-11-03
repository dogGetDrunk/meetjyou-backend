package com.dogGetDrunk.meetjyou.support

import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * One MySQL container per test JVM, started on first use. Each spec takes its own empty database
 * from [newDatabase] instead of starting a container, so MySQL boots once per run. The container
 * is never stopped here: Ryuk removes it when the JVM exits.
 */
object SharedMySql {
    private const val IMAGE = "mysql:8.0.41"
    // Cached Spring contexts keep their pools open for the whole run, which outgrows the default 151.
    private const val MAX_CONNECTIONS = 500
    private const val DATABASE_PREFIX = "spec_"
    private const val ROOT_USER = "root"

    private val sequence = AtomicInteger()

    private val container: MySQLContainer<*> by lazy {
        val mysql = MySQLContainer(DockerImageName.parse(IMAGE))
        mysql.withCommand("--max_connections=$MAX_CONNECTIONS")
        mysql.start()
        mysql
    }

    fun newDatabase(): MySqlDatabase {
        val name = "$DATABASE_PREFIX${sequence.incrementAndGet()}"
        // Testcontainers gives root the same password as the app user.
        DriverManager.getConnection(jdbcUrlOf(""), ROOT_USER, container.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE DATABASE `$name`")
                statement.execute("GRANT ALL PRIVILEGES ON `$name`.* TO '${container.username}'@'%'")
            }
        }
        return MySqlDatabase(name, jdbcUrlOf(name), container.username, container.password, ROOT_USER)
    }

    // Keeps the driver parameters Testcontainers puts on its own URL (useSSL, allowPublicKeyRetrieval).
    private fun jdbcUrlOf(database: String): String {
        val parameters = container.jdbcUrl.substringAfter('?', "")
        val base = "jdbc:mysql://${container.host}:${container.getMappedPort(MySQLContainer.MYSQL_PORT)}/$database"
        return if (parameters.isEmpty()) base else "$base?$parameters"
    }
}

/** Connection details of one database on the shared server. */
class MySqlDatabase internal constructor(
    val name: String,
    val jdbcUrl: String,
    val username: String,
    val password: String,
    private val rootUser: String,
) {
    /** Root connection for server-wide views (performance_schema, SHOW ENGINE) the app user can't read. */
    fun rootConnection(): Connection = DriverManager.getConnection(jdbcUrl, rootUser, password)

    /**
     * Lock waits whose requested lock is on a table of this database. data_lock_waits is
     * server-wide, so without the join a wait in another spec's database would count here.
     */
    fun countLockWaits(): Int =
        rootConnection().use { connection ->
            connection.prepareStatement(COUNT_LOCK_WAITS).use { statement ->
                statement.setString(1, name)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    private companion object {
        val COUNT_LOCK_WAITS = """
            SELECT COUNT(*)
            FROM performance_schema.data_lock_waits w
            JOIN performance_schema.data_locks l
              ON l.ENGINE = w.ENGINE AND l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
            WHERE l.OBJECT_SCHEMA = ?
        """.trimIndent()
    }
}
