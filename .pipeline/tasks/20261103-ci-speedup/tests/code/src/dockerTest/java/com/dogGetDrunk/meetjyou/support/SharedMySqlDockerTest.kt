package com.dogGetDrunk.meetjyou.support

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import org.flywaydb.core.Flyway
import java.net.URI
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// AT-002
class SharedMySqlDockerTest : BehaviorSpec({
    Given("공유 MySQL에서 두 database를 생성하면") {
        val x = SharedMySql.newDatabase()
        val y = SharedMySql.newDatabase()
        val xUri = URI(x.jdbcUrl.removePrefix("jdbc:"))
        val yUri = URI(y.jdbcUrl.removePrefix("jdbc:"))

        When("각 접속 정보로 연결하면") {
            Then("같은 서버의 서로 다른 빈 database에 연결된다") {
                xUri.host shouldBe yUri.host
                xUri.port shouldBe yUri.port
                xUri.path shouldNotBe yUri.path
                DriverManager.getConnection(x.jdbcUrl, x.username, x.password).use { connection ->
                    currentDatabase(connection) shouldBe xUri.path.removePrefix("/")
                    tableCount(connection) shouldBe 0
                }
                DriverManager.getConnection(y.jdbcUrl, y.username, y.password).use { connection ->
                    currentDatabase(connection) shouldBe yUri.path.removePrefix("/")
                    tableCount(connection) shouldBe 0
                }
            }
        }
        When("한 database에만 Flyway 마이그레이션을 적용하면") {
            val migration = Flyway.configure().dataSource(x.jdbcUrl, x.username, x.password).load().migrate()
            Then("마이그레이션된 database에만 테이블이 생성된다") {
                migration.success shouldBe true
                DriverManager.getConnection(x.jdbcUrl, x.username, x.password).use { connection ->
                    tableCount(connection) shouldBeGreaterThanOrEqual 1
                }
                DriverManager.getConnection(y.jdbcUrl, y.username, y.password).use { connection ->
                    tableCount(connection) shouldBe 0
                }
            }
        }
    }
    Given("서로 다른 database와 첫 database의 행 락이 있으면") {
        val x = SharedMySql.newDatabase()
        val y = SharedMySql.newDatabase()
        When("다른 트랜잭션이 같은 행의 락을 기다리면") {
            Then("대기는 해당 database에서만 관찰된다") {
                val executor = Executors.newSingleThreadExecutor()
                try {
                    DriverManager.getConnection(x.jdbcUrl, x.username, x.password).use { locker ->
                        locker.createStatement().use { statement ->
                            statement.execute("CREATE TABLE lock_probe (id INT PRIMARY KEY, value INT NOT NULL)")
                            statement.executeUpdate("INSERT INTO lock_probe VALUES (1, 0)")
                        }
                        locker.autoCommit = false
                        locker.createStatement().use { it.executeUpdate("UPDATE lock_probe SET value = 1 WHERE id = 1") }
                        val waiting = executor.submit {
                            DriverManager.getConnection(x.jdbcUrl, x.username, x.password).use { connection ->
                                connection.autoCommit = false
                                connection.createStatement().use { it.executeUpdate("UPDATE lock_probe SET value = 2 WHERE id = 1") }
                                connection.commit()
                            }
                        }
                        try {
                            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                            var waits = x.countLockWaits().toLong()
                            while (waits < 1L && System.nanoTime() < deadline && !waiting.isDone) {
                                Thread.sleep(50)
                                waits = x.countLockWaits().toLong()
                            }
                            waits shouldBeGreaterThanOrEqual 1L
                            waiting.isDone shouldBe false
                            y.countLockWaits().toLong() shouldBe 0L
                            waiting.isDone shouldBe false
                            x.countLockWaits().toLong() shouldBeGreaterThanOrEqual 1L
                        } finally {
                            locker.rollback()
                            waiting.get(20, TimeUnit.SECONDS)
                        }
                    }
                } finally {
                    executor.shutdownNow()
                    executor.awaitTermination(20, TimeUnit.SECONDS) shouldBe true
                }
            }
        }
    }
})

private fun currentDatabase(connection: Connection): String =
    connection.createStatement().use { statement ->
        statement.executeQuery("SELECT DATABASE()").use { result ->
            result.next()
            result.getString(1)
        }
    }

private fun tableCount(connection: Connection): Int =
    connection.createStatement().use { statement ->
        statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()").use { result ->
            result.next()
            result.getInt(1)
        }
    }
