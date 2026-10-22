package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.common.exception.business.user.DuplicateNicknameException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.support.assertWaitedOnLock
import com.dogGetDrunk.meetjyou.terms.TermsService
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
import com.dogGetDrunk.meetjyou.user.dto.UserUpdateRequest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
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
 * Sign-up and profile update race on the user row's unique indexes (nickname, external_id) and
 * on user_social_account. Both requests pass the existence checks, and MySQL makes the second
 * insert wait for the first one to commit and then fail. The loser must get a 409 exception
 * (or, for a double-submitted sign-up, the winner's account), never a raw
 * DataIntegrityViolationException that ends as a 500.
 *
 * The "holder" writes the winning row in a transaction kept open until the contender is seen
 * waiting on a lock in performance_schema.data_lock_waits. The contender is a plain service call:
 * the services open their own transactions, as they do in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserUniqueRaceDockerTest : BehaviorSpec() {

    // Every entity (preference entities reference post ones), but only the repositories these
    // services use: the application class's @ConfigurationPropertiesScan would bind properties
    // classes this slice has no values for.
    @SpringBootConfiguration
    @EntityScan(basePackages = [ROOT_PACKAGE])
    @EnableJpaRepositories(basePackageClasses = [UserRepository::class, PreferenceRepository::class, RefreshTokenRepository::class])
    class Config {
        @Bean
        fun jwtProvider() = JwtProvider(JWT_SECRET, ACCESS_EXPIRATION_MILLIS, REFRESH_EXPIRATION_MILLIS, ISSUER)

        @Bean
        fun currentUserProvider(): CurrentUserProvider = mockk()

        @Bean
        fun socialVerifierRegistry(): SocialVerifierRegistry = mockk()

        @Bean
        fun userService(
            userRepository: UserRepository,
            preferenceRepository: PreferenceRepository,
            userPreferenceRepository: UserPreferenceRepository,
            currentUserProvider: CurrentUserProvider,
        ) = UserService(
            userRepository = userRepository,
            preferenceRepository = preferenceRepository,
            userPreferenceRepository = userPreferenceRepository,
            currentUserProvider = currentUserProvider,
            termsService = mockk<TermsService>(relaxed = true) {
                every { validateRequiredTermsAgreement(any()) } returns emptyList()
            },
            userSessionRevoker = mockk(relaxed = true),
        )

        @Bean
        fun tokenPairIssuer(jwtProvider: JwtProvider, refreshTokenRepository: RefreshTokenRepository) =
            TokenPairIssuer(jwtProvider, refreshTokenRepository)

        @Bean
        fun socialRegistrationService(
            socialVerifierRegistry: SocialVerifierRegistry,
            userRepository: UserRepository,
            userService: UserService,
            tokenPairIssuer: TokenPairIssuer,
            transactionManager: PlatformTransactionManager,
        ) = SocialRegistrationService(
            socialVerifierRegistry = socialVerifierRegistry,
            userRepository = userRepository,
            userService = userService,
            tokenPairIssuer = tokenPairIssuer,
            transactionTemplate = TransactionTemplate(transactionManager),
            rotationOverlapSeconds = ROTATION_OVERLAP_SECONDS,
        )
    }

    @Autowired
    private lateinit var socialRegistrationService: SocialRegistrationService

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var currentUserProvider: CurrentUserProvider

    @Autowired
    private lateinit var socialVerifierRegistry: SocialVerifierRegistry

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var dataSource: DataSource

    private val currentUserUuid = ThreadLocal<UUID>()
    private val socialVerifier = mockk<SocialVerifier>()
    private val sequence = AtomicInteger()
    private val tx by lazy { TransactionTemplate(transactionManager) }
    private val jdbc by lazy { JdbcTemplate(dataSource) }

    private fun register(request: RegistrationRequest): TokenResponse = socialRegistrationService.register(request, NONCE)

    /** Writes a user with a Kakao identity, as a sign-up would. */
    private fun saveUser(nickname: String, subject: String): User {
        val user = User(email = null, nickname = nickname, authProvider = AuthProvider.KAKAO, externalId = subject)
        user.addSocialAccount(AuthProvider.KAKAO, subject, null)
        return userRepository.save(user)
    }

    private fun registrationRequest(nickname: String) = RegistrationRequest(
        nickname = nickname,
        bio = null,
        gender = Gender.M,
        age = Age.TWENTY,
        personalities = listOf(Personality.SOCIAL),
        travelStyles = emptyList(),
        diet = emptyList(),
        etc = emptyList(),
        authProvider = AuthProvider.KAKAO,
        idToken = ID_TOKEN,
        agreedTermsUuids = emptyList(),
    )

    private fun updateRequest(nickname: String) = UserUpdateRequest(
        nickname = nickname,
        bio = null,
        gender = Gender.F,
        age = Age.THIRTY,
        personalities = listOf(Personality.SOCIAL),
        travelStyles = emptyList(),
        diet = emptyList(),
        etc = emptyList(),
    )

    private fun stubVerifiedSubject(subject: String) {
        // Exact arguments: mockk's any() can't match the value-class SocialToken implementations.
        every { socialVerifier.verifyAndExtract(IdToken(ID_TOKEN), NONCE) } returns
            SocialPrincipal(AuthProvider.KAKAO, subject, null)
    }

    private fun countUsersWithNickname(nickname: String): Int =
        jdbc.queryForObject(COUNT_BY_NICKNAME, Int::class.java, nickname) ?: 0

    private fun countSocialAccounts(subject: String): Int =
        jdbc.queryForObject(COUNT_BY_SUBJECT, Int::class.java, subject) ?: 0

    private fun unique(prefix: String) = "$prefix${sequence.incrementAndGet()}"

    private class Contention<T>(val contender: Result<T>, val contenderWaitedOnLock: Boolean)

    /**
     * Runs [holder] in a transaction kept open until [contender], started on a second thread with
     * no surrounding transaction, waits on a lock (or the timeout passes), then commits the holder
     * and reports how the contender ended.
     */
    private fun <T> contend(holder: () -> Unit, contender: () -> T): Contention<T> {
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
            val contenderDone = executor.submit<Result<T>> { runCatching { contender() } }
            val waited = awaitLockWait()
            release.countDown()
            holderDone.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return Contention(contenderDone.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), waited)
        } finally {
            executor.shutdownNow()
        }
    }

    // performance_schema.data_lock_waits, not innodb_trx: a sign-up insert waiting on the nickname
    // index was seen listed as RUNNING there while data_lock_waits held its wait. Root only: the
    // app user lacks the privilege.
    private fun awaitLockWait(): Boolean =
        DriverManager.getConnection(mysql.jdbcUrl, ROOT_USER, mysql.password).use { root ->
            val deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MILLIS
            while (System.currentTimeMillis() < deadline) {
                if (countLockWaits(root) == 1) return@use true
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

        private const val ROOT_PACKAGE = "com.dogGetDrunk.meetjyou"
        private const val POOL_SIZE = 5
        private const val ROOT_USER = "root"
        private const val ID_TOKEN = "id-token"
        private const val NONCE = "nonce"
        private const val JWT_SECRET = "ZG9ja2VyLXRlc3Qtc2VjcmV0LWtleS0wMTIzNDU2Nzg5YWJjZGVm"
        private const val ACCESS_EXPIRATION_MILLIS = 1_800_000L
        private const val REFRESH_EXPIRATION_MILLIS = 2_592_000_000L
        private const val ISSUER = "meetjyou-docker-test"
        private const val ROTATION_OVERLAP_SECONDS = 30L
        private const val TIMEOUT_SECONDS = 30L
        private const val LOCK_WAIT_TIMEOUT_MILLIS = 10_000L
        private const val POLL_INTERVAL_MILLIS = 50L
        private const val COUNT_LOCK_WAITS = "SELECT COUNT(*) FROM performance_schema.data_lock_waits"
        private const val COUNT_BY_NICKNAME = "SELECT COUNT(*) FROM `user` WHERE nickname = ?"
        private const val COUNT_BY_SUBJECT =
            "SELECT COUNT(*) FROM user_social_account WHERE provider = 'KAKAO' AND external_id = ?"
    }

    init {
        extensions(SpringExtension())

        beforeSpec {
            every { socialVerifierRegistry.get(AuthProvider.KAKAO) } returns socialVerifier
            every { currentUserProvider.user } answers {
                val uuid = currentUserUuid.get()
                userRepository.findByUuid(uuid) ?: error("User $uuid not seeded")
            }
        }

        given("서로 다른 두 사람이") {
            `when`("같은 닉네임으로 동시에 가입하면") {
                then("한 명만 가입되고 진 쪽은 500이 아닌 닉네임 중복 409용 예외를 받는다") {
                    val nickname = unique("nick")
                    val loserSubject = unique("loser-")
                    stubVerifiedSubject(loserSubject)

                    val contention = contend(
                        holder = { saveUser(nickname, unique("winner-")) },
                        contender = { register(registrationRequest(nickname)) },
                    )

                    contention.contender.exceptionOrNull().shouldBeInstanceOf<DuplicateNicknameException>()
                    countUsersWithNickname(nickname) shouldBe 1
                    countSocialAccounts(loserSubject) shouldBe 0
                    assertWaitedOnLock(contention.contenderWaitedOnLock, contention.contender)
                }
            }
        }

        given("한 사람이 가입 요청을 두 번 보내서(더블탭)") {
            `when`("같은 닉네임의 두 요청이 겹치면") {
                then("진 요청도 먼저 만들어진 계정으로 로그인된다") {
                    val nickname = unique("dbl")
                    val subject = unique("double-")
                    stubVerifiedSubject(subject)
                    lateinit var winner: User

                    val contention = contend(
                        holder = { winner = saveUser(nickname, subject) },
                        contender = { register(registrationRequest(nickname)) },
                    )

                    contention.contender.getOrThrow().uuid shouldBe winner.uuid
                    countUsersWithNickname(nickname) shouldBe 1
                    countSocialAccounts(subject) shouldBe 1
                    assertWaitedOnLock(contention.contenderWaitedOnLock, contention.contender)
                }
            }

            `when`("두 요청의 닉네임이 달라 소셜 계정 unique에서 겹치면") {
                then("진 요청도 먼저 만들어진 계정으로 로그인된다") {
                    val subject = unique("double-")
                    val loserNickname = unique("dbb")
                    stubVerifiedSubject(subject)
                    lateinit var winner: User

                    val contention = contend(
                        holder = { winner = saveUser(unique("dba"), subject) },
                        contender = { register(registrationRequest(loserNickname)) },
                    )

                    contention.contender.getOrThrow().uuid shouldBe winner.uuid
                    countUsersWithNickname(loserNickname) shouldBe 0
                    countSocialAccounts(subject) shouldBe 1
                    assertWaitedOnLock(contention.contenderWaitedOnLock, contention.contender)
                }
            }
        }

        given("두 유저가") {
            `when`("프로필 수정으로 같은 닉네임을 동시에 가져가면") {
                then("한 명만 바뀌고 진 쪽은 500이 아닌 닉네임 중복 409용 예외를 받는다") {
                    val nickname = unique("upd")
                    val first = saveUser(unique("upa"), unique("upd-a-"))
                    val second = saveUser(unique("upb"), unique("upd-b-"))

                    val contention = contend(
                        holder = {
                            val user = userRepository.findByUuid(first.uuid) ?: error("User not seeded")
                            user.nickname = nickname
                            userRepository.flush()
                        },
                        contender = {
                            currentUserUuid.set(second.uuid)
                            userService.updateUser(updateRequest(nickname))
                        },
                    )

                    contention.contender.exceptionOrNull().shouldBeInstanceOf<DuplicateNicknameException>()
                    countUsersWithNickname(nickname) shouldBe 1
                    assertWaitedOnLock(contention.contenderWaitedOnLock, contention.contender)
                }
            }
        }
    }
}
