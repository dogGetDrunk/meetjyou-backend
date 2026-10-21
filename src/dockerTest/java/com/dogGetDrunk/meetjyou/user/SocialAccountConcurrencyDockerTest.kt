package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.common.exception.business.user.LastSocialAccountUnlinkException
import com.dogGetDrunk.meetjyou.common.exception.business.user.SocialAccountAlreadyLinkedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.user.dto.LinkSocialAccountRequest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Runs SocialAccountService against a real MySQL, two transactions at a time. H2 neither locks
 * nor snapshots like InnoDB, so the unlink race and the unique-constraint race can only be
 * checked here. Each scenario is made deterministic: the "holder" transaction does its work and
 * stays open until the "contender" is seen waiting on a lock in performance_schema.data_lock_waits.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SocialAccountConcurrencyDockerTest : BehaviorSpec() {

    // Only the user package: the application class's @ConfigurationPropertiesScan would bind
    // every OIDC/OCI properties class, which this slice has no values for.
    @SpringBootConfiguration
    @EntityScan(basePackageClasses = [User::class])
    @EnableJpaRepositories(basePackageClasses = [UserRepository::class])
    class Config

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var dataSource: DataSource

    private val currentUserUuid = ThreadLocal<UUID>()
    private val socialVerifier = mockk<SocialVerifier>()
    private val socialVerifierRegistry = mockk<SocialVerifierRegistry>()
    private val currentUserProvider = mockk<CurrentUserProvider>()
    private val sequence = AtomicInteger()

    private val sut by lazy { SocialAccountService(socialVerifierRegistry, userRepository, currentUserProvider) }
    private val tx by lazy { TransactionTemplate(transactionManager) }
    private val jdbc by lazy { JdbcTemplate(dataSource) }

    private fun seedUser(vararg providers: AuthProvider): UUID {
        val n = sequence.incrementAndGet()
        val user = User(email = "c$n@example.com", nickname = "conc$n", authProvider = providers.first(), externalId = "conc-$n")
        providers.forEach { user.addSocialAccount(it, "${it.name}-$n", "c$n@example.com") }
        return userRepository.save(user).uuid
    }

    private fun providersOf(uuid: UUID): List<AuthProvider> =
        jdbc.queryForList(SELECT_PROVIDERS, String::class.java, uuid.toString()).map { AuthProvider.valueOf(it) }

    private fun stubVerifiedPrincipal(provider: AuthProvider, subject: String) {
        every { socialVerifierRegistry.get(provider) } returns socialVerifier
        // Exact arguments: mockk's any() can't match the value-class SocialToken implementations.
        every { socialVerifier.verifyAndExtract(IdToken(ID_TOKEN), NONCE) } returns
            SocialPrincipal(provider, subject, "linked@example.com")
    }

    private fun linkAs(uuid: UUID, provider: AuthProvider) {
        currentUserUuid.set(uuid)
        sut.link(LinkSocialAccountRequest(provider, idToken = ID_TOKEN), NONCE)
    }

    private class Contention(val contender: Result<Unit>, val contenderWaitedOnLock: Boolean)

    /**
     * Runs [holder] in a transaction kept open until [contender], started in a second transaction,
     * waits on a lock (or [maxLockWaitMillis] passes), then commits the holder and reports how
     * the contender ended.
     */
    private fun contend(holder: () -> Unit, contender: () -> Unit, maxLockWaitMillis: Long = LOCK_WAIT_TIMEOUT_MILLIS): Contention {
        val holderReady = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val holderDone = executor.submit {
                tx.executeWithoutResult {
                    holder()
                    holderReady.countDown()
                    release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
            }
            holderReady.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
            val contenderDone = executor.submit<Result<Unit>> { runCatching { tx.executeWithoutResult { contender() } } }
            val waited = awaitLockWait(maxLockWaitMillis)
            release.countDown()
            holderDone.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return Contention(contenderDone.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), waited)
        } finally {
            executor.shutdownNow()
        }
    }

    // performance_schema needs more than the app user's privileges. data_lock_waits, not
    // innodb_trx.trx_state: the latter is a cached view and missed a real wait in CI (#200).
    private fun awaitLockWait(maxMillis: Long): Boolean =
        DriverManager.getConnection(mysql.jdbcUrl, ROOT_USER, mysql.password).use { root ->
            val deadline = System.currentTimeMillis() + maxMillis
            while (System.currentTimeMillis() < deadline) {
                if (countLockWaits(root) >= 1) return@use true
                Thread.sleep(POLL_INTERVAL_MILLIS)
            }
            false
        }

    private fun countLockWaits(connection: Connection): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery(COUNT_LOCK_WAITS).use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    companion object {
        private val mysql = MySQLContainer(DockerImageName.parse("mysql:8.0.41")).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.datasource.hikari.maximum-pool-size") { POOL_SIZE }
            registry.add("spring.datasource.hikari.minimum-idle") { POOL_SIZE }
        }

        private const val POOL_SIZE = 5
        private const val ROOT_USER = "root"
        private const val ID_TOKEN = "id-token"
        private const val NONCE = "nonce"
        private const val TIMEOUT_SECONDS = 30L
        private const val LOCK_WAIT_TIMEOUT_MILLIS = 10_000L
        private const val NO_LOCK_EXPECTED_WAIT_MILLIS = 2_000L
        private const val POLL_INTERVAL_MILLIS = 50L
        private const val COUNT_LOCK_WAITS = "SELECT COUNT(*) FROM performance_schema.data_lock_waits"
        private const val SELECT_PROVIDERS =
            "SELECT a.provider FROM user_social_account a JOIN `user` u ON u.id = a.user_id WHERE u.uuid = ?"
    }

    init {
        extensions(SpringExtension())

        beforeSpec {
            every { currentUserProvider.user } answers {
                val uuid = currentUserUuid.get()
                userRepository.findByUuid(uuid) ?: error("User $uuid not seeded")
            }
        }

        given("카카오·구글이 연결된 유저가 두 기기에서") {
            `when`("카카오와 구글 해제를 동시에 요청하면") {
                then("나중 요청은 앞 요청이 남긴 1개를 보고 거부되어 계정이 하나 남는다") {
                    val uuid = seedUser(AuthProvider.KAKAO, AuthProvider.GOOGLE)

                    val contention = contend(
                        holder = {
                            currentUserUuid.set(uuid)
                            sut.unlink(AuthProvider.KAKAO)
                        },
                        contender = {
                            currentUserUuid.set(uuid)
                            // Reads the user and its collection before the service runs, pinning
                            // this transaction's snapshot while both rows are still visible.
                            currentUserProvider.user.socialAccounts.size shouldBe 2
                            sut.unlink(AuthProvider.GOOGLE)
                        },
                    )

                    providersOf(uuid) shouldBe listOf(AuthProvider.GOOGLE)
                    contention.contender.exceptionOrNull().shouldBeInstanceOf<LastSocialAccountUnlinkException>()
                    contention.contenderWaitedOnLock shouldBe true
                }
            }
        }

        given("두 유저가") {
            `when`("같은 구글 계정을 동시에 연결하면") {
                then("한 명만 연결되고 다른 한 명은 500이 아닌 409용 예외를 받는다") {
                    val first = seedUser(AuthProvider.KAKAO)
                    val second = seedUser(AuthProvider.KAKAO)
                    stubVerifiedPrincipal(AuthProvider.GOOGLE, "shared-google-sub")

                    val contention = contend(
                        holder = { linkAs(first, AuthProvider.GOOGLE) },
                        contender = { linkAs(second, AuthProvider.GOOGLE) },
                    )

                    contention.contender.exceptionOrNull().shouldBeInstanceOf<SocialAccountAlreadyLinkedException>()
                    providersOf(first) shouldContainExactlyInAnyOrder listOf(AuthProvider.KAKAO, AuthProvider.GOOGLE)
                    providersOf(second) shouldBe listOf(AuthProvider.KAKAO)
                    contention.contenderWaitedOnLock shouldBe true
                }
            }
        }

        given("카카오·구글이 연결된 유저가") {
            `when`("구글 해제 중에 애플 연결을 동시에 요청하면") {
                then("데드락 없이 둘 다 성공한다") {
                    val uuid = seedUser(AuthProvider.KAKAO, AuthProvider.GOOGLE)
                    stubVerifiedPrincipal(AuthProvider.APPLE, "apple-sub-$uuid")

                    val contention = contend(
                        holder = {
                            currentUserUuid.set(uuid)
                            sut.unlink(AuthProvider.GOOGLE)
                        },
                        contender = { linkAs(uuid, AuthProvider.APPLE) },
                        maxLockWaitMillis = NO_LOCK_EXPECTED_WAIT_MILLIS,
                    )

                    contention.contender.isSuccess shouldBe true
                    providersOf(uuid) shouldContainExactlyInAnyOrder listOf(AuthProvider.KAKAO, AuthProvider.APPLE)
                }
            }
        }
    }
}
