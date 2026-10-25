package com.dogGetDrunk.meetjyou.party

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Guards #172: the party row lock must hand back the committed row even when the party was already
 * loaded earlier in the same transaction. Hibernate returns the managed instance for a re-query, and
 * under REPEATABLE READ a plain re-read sees the transaction's old snapshot — so only a locking
 * re-read gives the fresh `joined`. Needs real MySQL: H2 runs READ COMMITTED and can't tell them apart.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PartyLockFreshReadDockerTest : BehaviorSpec() {

    @SpringBootConfiguration
    @EntityScan(basePackages = [BASE_PACKAGE])
    @EnableJpaRepositories(basePackages = [BASE_PACKAGE])
    class Config

    @Autowired private lateinit var partyRepository: PartyRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val tx by lazy { TransactionTemplate(transactionManager) }
    private val newTx by lazy {
        TransactionTemplate(transactionManager).also { it.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW }
    }

    private fun seedParty(): UUID = tx.execute {
        val now = Instant.now()
        partyRepository.save(
            Party(
                itinStart = now.plus(1, ChronoUnit.DAYS), itinFinish = now.plus(TRIP_DAYS, ChronoUnit.DAYS),
                destination = LOCATION, joined = INITIAL_JOINED, capacity = CAPACITY, name = "Party",
            )
        ).uuid
    } ?: error("seed failed")

    companion object {
        private val mysql = MySQLContainer(DockerImageName.parse("mysql:8.0.41")).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
        }

        private const val BASE_PACKAGE = "com.dogGetDrunk.meetjyou"
        private const val INITIAL_JOINED = 2
        private const val CAPACITY = 4
        private const val TRIP_DAYS = 3L
        private const val LOCATION = "Busan"
    }

    init {
        extensions(SpringExtension())

        given("같은 트랜잭션에서 파티를 잠금 없이 먼저 읽어 둔 상태일 때") {
            `when`("그 사이 다른 트랜잭션이 정원을 채워 커밋하고, 원 트랜잭션이 파티 행을 잠그면") {
                then("잠금 결과는 커밋된 최신 joined를 담는다") {
                    val partyUuid = seedParty()

                    val lockedJoined = tx.execute {
                        partyRepository.findByUuid(partyUuid) ?: error("party missing")
                        newTx.executeWithoutResult {
                            val other = partyRepository.findByUuid(partyUuid) ?: error("party missing")
                            other.joined = CAPACITY
                        }
                        val locked = partyRepository.lockByUuid(partyUuid) ?: error("party missing")
                        locked.joined
                    }

                    lockedJoined shouldBe CAPACITY
                }
            }
        }
    }
}
