package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
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
 * Guards #222: the membership row lock must hand back the committed row even when the membership
 * was already read earlier in the same transaction (approve reads the host's membership first, which
 * fixes the REPEATABLE READ snapshot). Only a locking re-read sees a concurrent approve/reject, and a
 * concurrently deleted (cancelled) request must come back as null. Needs real MySQL: H2 runs
 * READ COMMITTED and can't tell a locking re-read from a plain one.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserPartyLockFreshReadDockerTest : BehaviorSpec() {

    @SpringBootConfiguration
    @EntityScan(basePackages = [BASE_PACKAGE])
    @EnableJpaRepositories(basePackages = [BASE_PACKAGE])
    class Config

    @Autowired private lateinit var partyRepository: PartyRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var userPartyRepository: UserPartyRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val tx by lazy { TransactionTemplate(transactionManager) }
    private val newTx by lazy {
        TransactionTemplate(transactionManager).also { it.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW }
    }

    private fun seedPendingRequest(): Pair<UUID, UUID> = tx.execute {
        val now = Instant.now()
        val party = partyRepository.save(
            Party(
                itinStart = now.plus(1, ChronoUnit.DAYS), itinFinish = now.plus(TRIP_DAYS, ChronoUnit.DAYS),
                destination = LOCATION, joined = 1, capacity = CAPACITY, name = "Party",
            )
        )
        val email = "applicant-${System.nanoTime()}@example.com"
        val nickname = "$NICKNAME_PREFIX${System.nanoTime() % NICKNAME_SUFFIX_RANGE}"
        val applicant = userRepository.save(
            User(email = email, nickname = nickname, authProvider = AuthProvider.KAKAO, externalId = email)
        )
        userPartyRepository.save(UserParty(party, applicant, PartyRole.MEMBER).also { it.pending() })
        party.uuid to applicant.uuid
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
        private const val CAPACITY = 4
        private const val TRIP_DAYS = 3L
        private const val LOCATION = "Busan"
        private const val NICKNAME_PREFIX = "ap"
        private const val NICKNAME_SUFFIX_RANGE = 1_000_000L
    }

    init {
        extensions(SpringExtension())

        given("같은 트랜잭션에서 신청 행을 잠금 없이 먼저 읽어 둔 상태일 때") {
            `when`("그 사이 다른 트랜잭션이 승인을 커밋하고, 원 트랜잭션이 신청 행을 잠그면") {
                then("잠금 결과는 커밋된 최신 상태(JOINED)를 담는다") {
                    val (partyUuid, userUuid) = seedPendingRequest()

                    val lockedStatus = tx.execute {
                        userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid) ?: error("membership missing")
                        newTx.executeWithoutResult {
                            val other = userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid)
                                ?: error("membership missing")
                            other.approve()
                        }
                        userPartyRepository.lockByPartyUuidAndUserUuid(partyUuid, userUuid)?.memberStatus
                    }

                    lockedStatus shouldBe MemberStatus.JOINED
                }
            }

            `when`("그 사이 다른 트랜잭션이 신청 행을 삭제(신청 취소)하고, 원 트랜잭션이 신청 행을 잠그면") {
                then("잠금 결과는 null이다") {
                    val (partyUuid, userUuid) = seedPendingRequest()

                    val locked = tx.execute {
                        userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid) ?: error("membership missing")
                        newTx.executeWithoutResult {
                            userPartyRepository.deleteByParty_UuidAndUser_Uuid(partyUuid, userUuid)
                        }
                        listOfNotNull(userPartyRepository.lockByPartyUuidAndUserUuid(partyUuid, userUuid))
                    }

                    locked shouldBe emptyList()
                }
            }
        }
    }
}
