package com.dogGetDrunk.meetjyou.common

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.UniqueConstraintConflicts
import com.dogGetDrunk.meetjyou.notification.push.PushToken
import com.dogGetDrunk.meetjyou.notification.push.PushTokenRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.UserPreference
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.support.SharedMySql
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.version.AppVersion
import com.dogGetDrunk.meetjyou.version.AppVersionRepository
import com.dogGetDrunk.meetjyou.version.Platform
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * The global unique-race handler maps by constraint name, which H2 can't check: the names must
 * exist in the real Flyway schema, and MySQL must report a lost insert in a shape
 * violatedConstraintName() reads ("table.index").
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UniqueConstraintConflictsDockerTest : BehaviorSpec() {

    @SpringBootConfiguration
    @EntityScan(basePackages = [ROOT_PACKAGE])
    @EnableJpaRepositories(
        basePackageClasses = [
            UserRepository::class, PreferenceRepository::class, PushTokenRepository::class, AppVersionRepository::class,
        ],
    )
    class Config

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var preferenceRepository: PreferenceRepository

    @Autowired
    private lateinit var userPreferenceRepository: UserPreferenceRepository

    @Autowired
    private lateinit var pushTokenRepository: PushTokenRepository

    @Autowired
    private lateinit var appVersionRepository: AppVersionRepository

    init {
        extensions(SpringExtension())

        given("Flyway로 만든 실제 MySQL 스키마") {
            `when`("매핑 대상 제약 이름을 조회하면") {
                then("모두 unique 인덱스로 존재한다") {
                    val found = jdbcTemplate.queryForList(FIND_UNIQUE_INDEXES, String::class.java)

                    found shouldContainExactlyInAnyOrder MAPPED_CONSTRAINTS
                }
            }

            `when`("V1에서 이름 없이 만든 push_token·chat_participant 인덱스를 조회하면") {
                then("AT-1-1 V42가 바꾼 뒤라 자동 생성 이름은 남아 있지 않다") {
                    jdbcTemplate.queryForList(FIND_AUTO_NAMED_INDEXES, String::class.java).shouldBeEmpty()
                }
            }
        }

        given("같은 유저·선호도 행을 두 번 insert하면") {
            `when`("두 번째 insert가 uq_user_preference에 걸리면") {
                then("MySQL 예외에서 제약 이름을 읽어 409 CONCURRENT_REQUEST_CONFLICT로 매핑한다") {
                    val user = saveUser()
                    val preference = preferenceRepository.findAll().first()
                    userPreferenceRepository.saveAndFlush(UserPreference(user, preference))

                    val e = shouldThrow<DataIntegrityViolationException> {
                        userPreferenceRepository.saveAndFlush(UserPreference(user, preference))
                    }

                    UniqueConstraintConflicts.errorCodeFor(e) shouldBe ErrorCode.CONCURRENT_REQUEST_CONFLICT
                }
            }
        }

        given("같은 푸시 토큰을 두 번 insert하면") {
            `when`("두 번째 insert가 uk_push_token_token에 걸리면") {
                then("AT-2-1 MySQL 예외에서 제약 이름을 읽어 409 CONCURRENT_REQUEST_CONFLICT로 매핑한다") {
                    val user = saveUser()
                    val version = "9.9.${UUID.randomUUID().hashCode()}"
                    val appVersion = appVersionRepository.save(
                        AppVersion(platform = Platform.ANDROID, version = version, forceUpdate = false),
                    )
                    val token = "token-${UUID.randomUUID()}"
                    pushTokenRepository.saveAndFlush(pushToken(token, user, appVersion))

                    val e = shouldThrow<DataIntegrityViolationException> {
                        pushTokenRepository.saveAndFlush(pushToken(token, user, appVersion))
                    }

                    UniqueConstraintConflicts.errorCodeFor(e) shouldBe ErrorCode.CONCURRENT_REQUEST_CONFLICT
                }
            }
        }
    }

    private fun pushToken(token: String, user: User, appVersion: AppVersion) =
        PushToken(token = token, platform = PushToken.PushPlatform.ANDROID, user = user, appVersion = appVersion)

    private fun saveUser(): User {
        val subject = "unique-race-${UUID.randomUUID()}"
        val user = User(email = null, nickname = subject.takeLast(NICKNAME_LENGTH), authProvider = AuthProvider.KAKAO, externalId = subject)
        user.addSocialAccount(AuthProvider.KAKAO, subject, null)
        return userRepository.save(user)
    }

    companion object {
        private val mysql = SharedMySql.newDatabase()

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
        }

        private const val ROOT_PACKAGE = "com.dogGetDrunk.meetjyou"
        private const val NICKNAME_LENGTH = 10
        private val MAPPED_CONSTRAINTS = listOf(
            "uk_user_party",
            "uq_user_preference",
            "uk_unp_user_type",
            "uk_push_token_token",
            "uk_chat_participant_user_room",
            "uq_comp_preference",
            "uk_terms_type_version",
            "uk_version_platform",
        )
        private val FIND_UNIQUE_INDEXES = """
            SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND NON_UNIQUE = 0
              AND INDEX_NAME IN (${MAPPED_CONSTRAINTS.joinToString { "'$it'" }})
        """.trimIndent()
        private val FIND_AUTO_NAMED_INDEXES = """
            SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND ((TABLE_NAME = 'push_token' AND INDEX_NAME = 'token')
                OR (TABLE_NAME = 'chat_participant' AND INDEX_NAME = 'user_id'))
        """.trimIndent()
    }
}
