package com.dogGetDrunk.meetjyou.common

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.UniqueConstraintConflicts
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.UserPreference
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
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
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

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
    @EnableJpaRepositories(basePackageClasses = [UserRepository::class, PreferenceRepository::class])
    class Config

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var preferenceRepository: PreferenceRepository

    @Autowired
    private lateinit var userPreferenceRepository: UserPreferenceRepository

    init {
        extensions(SpringExtension())

        given("Flyway로 만든 실제 MySQL 스키마") {
            `when`("매핑 대상 제약 이름을 조회하면") {
                then("모두 unique 인덱스로 존재한다") {
                    val found = jdbcTemplate.queryForList(FIND_UNIQUE_INDEXES, String::class.java)

                    found shouldContainExactlyInAnyOrder MAPPED_CONSTRAINTS
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
    }

    private fun saveUser(): User {
        val subject = "unique-race-${UUID.randomUUID()}"
        val user = User(email = null, nickname = subject.takeLast(NICKNAME_LENGTH), authProvider = AuthProvider.KAKAO, externalId = subject)
        user.addSocialAccount(AuthProvider.KAKAO, subject, null)
        return userRepository.save(user)
    }

    companion object {
        private val mysql = MySQLContainer(DockerImageName.parse("mysql:8.0.41")).apply { start() }

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
            "uk_terms_type_version",
            "uk_version_platform",
        )
        private val FIND_UNIQUE_INDEXES = """
            SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND NON_UNIQUE = 0
              AND INDEX_NAME IN (${MAPPED_CONSTRAINTS.joinToString { "'$it'" }})
        """.trimIndent()
    }
}
