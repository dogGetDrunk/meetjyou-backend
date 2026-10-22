package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshToken
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.CustomJwtException
import com.dogGetDrunk.meetjyou.support.assertWaitedOnLock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import java.sql.Connection
import java.sql.DriverManager
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
import org.springframework.context.annotation.Bean
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
 * Runs refresh-token rotation and logout against a real MySQL, two transactions at a time. H2
 * neither locks nor snapshots like InnoDB, so these races can only be checked here. The holder
 * transaction does its work and stays open until the contender is seen waiting on a lock in
 * performance_schema.data_lock_waits, which makes every interleaving deterministic.
 *
 * UserAuthService is a Spring bean here (not built by hand) so its own @Transactional rules,
 * rollback rules included, apply to the contender exactly as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshTokenRotationConcurrencyDockerTest : BehaviorSpec() {

    // Only the user and refresh-token packages: the application class's
    // @ConfigurationPropertiesScan would bind properties classes this slice has no values for.
    @SpringBootConfiguration
    @EntityScan(basePackageClasses = [User::class, RefreshToken::class])
    @EnableJpaRepositories(basePackageClasses = [UserRepository::class, RefreshTokenRepository::class])
    class Config {
        @Bean
        fun jwtProvider() = JwtProvider(JWT_SECRET, ACCESS_EXPIRATION_MILLIS, REFRESH_EXPIRATION_MILLIS, ISSUER)

        @Bean
        fun userAuthService(
            userRepository: UserRepository,
            refreshTokenRepository: RefreshTokenRepository,
            jwtProvider: JwtProvider,
        ) = UserAuthService(
            socialVerifierRegistry = mockk(relaxed = true),
            userRepository = userRepository,
            jwtProvider = jwtProvider,
            refreshTokenRepository = refreshTokenRepository,
            tokenPairIssuer = TokenPairIssuer(jwtProvider, refreshTokenRepository),
            rotationOverlapSeconds = ROTATION_OVERLAP_SECONDS,
        )
    }

    @Autowired
    private lateinit var sut: UserAuthService

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var dataSource: DataSource

    private val sequence = AtomicInteger()
    private val tx by lazy { TransactionTemplate(transactionManager) }
    private val jdbc by lazy { JdbcTemplate(dataSource) }

    private fun seedUser(): User {
        val n = sequence.incrementAndGet()
        return userRepository.save(User(email = null, nickname = "rt$n", authProvider = AuthProvider.KAKAO, externalId = "rt-$n"))
    }

    /** Stores a fresh refresh token for [user], as a login would, and returns the raw JWT. */
    private fun issueInitialToken(user: User): String {
        val generated = jwtProvider.generateRefreshToken(user.uuid)
        refreshTokenRepository.save(RefreshToken(jti = generated.jti.toString(), user = user, expiresAt = generated.expiresAt))
        return generated.token
    }

    private fun rotate(rawRefreshToken: String): String = sut.refreshToken(rawRefreshToken).refreshToken

    private fun validTokenCount(user: User): Int =
        jdbc.queryForObject(COUNT_VALID_TOKENS, Int::class.java, user.id) ?: 0

    private fun replacedByOf(rawRefreshToken: String): String? =
        jdbc.queryForObject(SELECT_REPLACED_BY, String::class.java, jwtProvider.getJti(rawRefreshToken))

    private fun jtiOf(rawRefreshToken: String): String = jwtProvider.getJti(rawRefreshToken)

    private class Contention(val contender: Result<Unit>, val contenderWaitedOnLock: Boolean)

    /**
     * Runs [holder] in a transaction kept open until [contender] waits on a lock (or
     * [maxLockWaitMillis] passes), then commits the holder and reports how the contender ended.
     * The contender runs outside any test transaction so the service's own one decides commit
     * or rollback.
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
            val contenderDone = executor.submit<Result<Unit>> { runCatching { contender() } }
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

        private const val JWT_SECRET = "ZG9ja2VyLXRlc3Qtc2VjcmV0LWtleS0wMTIzNDU2Nzg5YWJjZGVm"
        private const val ISSUER = "meetjyou-docker-test"
        private const val ACCESS_EXPIRATION_MILLIS = 1_800_000L
        private const val REFRESH_EXPIRATION_MILLIS = 2_592_000_000L
        private const val ROTATION_OVERLAP_SECONDS = 30L
        private const val POOL_SIZE = 5
        private const val ROOT_USER = "root"
        private const val TIMEOUT_SECONDS = 30L
        private const val LOCK_WAIT_TIMEOUT_MILLIS = 10_000L
        private const val POLL_INTERVAL_MILLIS = 50L
        private const val COUNT_LOCK_WAITS = "SELECT COUNT(*) FROM performance_schema.data_lock_waits"
        private const val COUNT_VALID_TOKENS =
            "SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked = FALSE AND expires_at > NOW()"
        private const val SELECT_REPLACED_BY = "SELECT replaced_by_jti FROM refresh_token WHERE jti = ?"

        // The newest row for a user: the token the rotation chain currently ends at.
        private const val SELECT_HEAD_JTI = "SELECT jti FROM refresh_token WHERE user_id = ? ORDER BY id DESC LIMIT 1"
    }

    init {
        extensions(SpringExtension())

        given("유효한 refresh token A 하나로") {
            `when`("refresh를 동시에 두 번 요청하면") {
                then("둘 다 성공하지만 살아있는 토큰은 하나만 남는다") {
                    val user = seedUser()
                    val a = issueInitialToken(user)

                    val contention = contend(
                        holder = { sut.refreshToken(a) },
                        contender = { sut.refreshToken(a) },
                    )

                    contention.contender.getOrThrow()
                    validTokenCount(user) shouldBe 1
                    assertWaitedOnLock(contention.contenderWaitedOnLock, contention.contender)
                }
            }
        }

        given("A가 방금 B로 회전된 상태에서") {
            `when`("A의 grace 재시도가 먼저 잡고 B의 refresh가 겹치면") {
                then("B refresh는 재시도가 만든 토큰에서 다시 회전해 살아있는 토큰이 하나만 남는다") {
                    val user = seedUser()
                    val a = issueInitialToken(user)
                    val b = rotate(a)

                    val contention = contend(
                        holder = { sut.refreshToken(a) },
                        contender = { sut.refreshToken(b) },
                    )

                    contention.contender.getOrThrow()
                    validTokenCount(user) shouldBe 1
                    assertWaitedOnLock(contention.contenderWaitedOnLock, contention.contender)
                }
            }

            `when`("B의 refresh가 먼저 잡고 A의 grace 재시도가 겹치면") {
                then("재시도는 이미 소비된 B를 보고 탈취로 처리되어 살아있는 토큰이 없다") {
                    val user = seedUser()
                    val a = issueInitialToken(user)
                    val b = rotate(a)

                    val contention = contend(
                        holder = { sut.refreshToken(b) },
                        contender = { sut.refreshToken(a) },
                    )

                    contention.contender.exceptionOrNull().shouldBeInstanceOf<CustomJwtException>()
                    validTokenCount(user) shouldBe 0
                    assertWaitedOnLock(contention.contenderWaitedOnLock, contention.contender)
                }
            }
        }

        given("A → B → C로 두 번 회전된 상태에서") {
            `when`("A를 다시 쓰면") {
                then("재사용 탐지로 그 유저의 토큰이 모두 폐기된 채 커밋된다") {
                    val user = seedUser()
                    val a = issueInitialToken(user)
                    val b = rotate(a)
                    rotate(b)

                    shouldThrow<CustomJwtException> { sut.refreshToken(a) }

                    validTokenCount(user) shouldBe 0
                }
            }
        }

        given("유효한 refresh token A로") {
            `when`("refresh와 logout이 겹치면") {
                then("refresh가 만든 새 토큰까지 폐기되고 체인 연결은 유지된다") {
                    val user = seedUser()
                    val a = issueInitialToken(user)

                    val contention = contend(
                        holder = { sut.refreshToken(a) },
                        contender = { sut.logout(a) },
                    )

                    contention.contender.getOrThrow()
                    validTokenCount(user) shouldBe 0
                    assertWaitedOnLock(contention.contenderWaitedOnLock, contention.contender)
                    replacedByOf(a) shouldBe jdbc.queryForObject(SELECT_HEAD_JTI, String::class.java, user.id)
                }
            }
        }

        given("A가 이미 B로 회전된 상태에서") {
            `when`("A로 logout하면") {
                then("B도 폐기되고 A → B 연결은 지워지지 않는다") {
                    val user = seedUser()
                    val a = issueInitialToken(user)
                    val b = rotate(a)

                    sut.logout(a)

                    validTokenCount(user) shouldBe 0
                    replacedByOf(a) shouldBe jtiOf(b)
                }
            }
        }
    }
}

