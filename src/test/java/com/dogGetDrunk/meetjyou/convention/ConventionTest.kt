package com.dogGetDrunk.meetjyou.convention

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.io.File

/**
 * Enforces CLAUDE.md conventions that detekt cannot check without type resolution.
 * See docs/agent-process/README.md (L2).
 */
class ConventionTest : BehaviorSpec({
    val productionFiles = Konsist.scopeFromProduction().files

    Given("프로덕션 코드 전체") {
        // A Kotest test name starting with "!" is silently disabled, so keep "!!" out of the prefix.
        Then("non-null 단언 연산자(!!)를 쓰지 않는다 (?: throw 사용)") {
            productionFiles.assertFalse { file -> codeLines(file).any { NOT_NULL_ASSERTION.containsMatchIn(it) } }
        }
        Then("requireNotNull / checkNotNull을 쓰지 않는다") {
            productionFiles.assertFalse { file -> codeLines(file).any { NULL_CHECK_CALL.containsMatchIn(it) } }
        }
    }

    Given("컨트롤러") {
        Then("DTO의 of() 팩토리를 직접 호출하지 않는다 (서비스가 DTO 반환)") {
            productionFiles.withNameEndingWith(CONTROLLER_SUFFIX)
                .assertFalse { file -> codeLines(file).any { DTO_FACTORY_CALL.containsMatchIn(it) } }
        }
    }

    // application.yml sets useLocalSessionState=true: Connector/J trusts its cached autocommit,
    // isolation and read-only values instead of asking MySQL. SQL that changes them behind the
    // driver's back leaves the cache wrong, e.g. a pooled connection the driver thinks is
    // autocommit while MySQL holds an open transaction. Flyway shares the app DataSource, so
    // migrations count too. Change session state only through the JDBC/Spring APIs.
    Given("프로덕션 코드와 Flyway 마이그레이션") {
        Then("SQL로 autocommit·트랜잭션 격리 수준·접근 모드를 바꾸지 않는다 (useLocalSessionState)") {
            val codeOffenders = productionFiles.filter { containsSessionStateSql(codeLines(it)) }.map { it.path }
            // An empty scan (wrong working directory) would pass vacuously.
            migrationFiles().shouldNotBeEmpty()
            val migrationOffenders = migrationFiles().filter { containsSessionStateSql(it.readLines()) }.map { it.path }
            (codeOffenders + migrationOffenders).shouldBeEmpty()
        }
    }

    Given("서비스") {
        Then("SecurityUtil을 직접 쓰지 않는다 (CurrentUserProvider 사용)") {
            productionFiles.withNameEndingWith(SERVICE_SUFFIX)
                .assertFalse { file -> file.imports.any { it.name.endsWith(SECURITY_UTIL) } }
        }
    }
})

private const val CONTROLLER_SUFFIX = "Controller"
private const val SERVICE_SUFFIX = "Service"
private const val SECURITY_UTIL = "SecurityUtil"
private const val LINE_COMMENT = "//"

private val NOT_NULL_ASSERTION = Regex("""!!""")
private val NULL_CHECK_CALL = Regex("""\b(requireNotNull|checkNotNull)\s*\(""")
private val DTO_FACTORY_CALL = Regex("""\b[A-Z]\w*(Response|Dto)\.of\(""")

private const val MIGRATION_DIR = "src/main/resources/db/migration"
private const val SQL_EXTENSION = "sql"
private const val SET_SCOPE = """\bSET\s+(?:(?:SESSION|LOCAL|GLOBAL)\s+)?(?:@@(?:session\.|global\.)?)?"""
private val SESSION_STATE_SQL = listOf(
    Regex("""${SET_SCOPE}autocommit\b""", RegexOption.IGNORE_CASE),
    Regex("""${SET_SCOPE}TRANSACTION\s+(?:ISOLATION|READ)\b""", RegexOption.IGNORE_CASE),
    Regex("""${SET_SCOPE}transaction_(?:isolation|read_only)\b""", RegexOption.IGNORE_CASE),
)

private fun containsSessionStateSql(lines: List<String>): Boolean =
    lines.any { line -> SESSION_STATE_SQL.any { it.containsMatchIn(line) } }

private fun migrationFiles(): List<File> =
    File(MIGRATION_DIR).walkTopDown().filter { it.isFile && it.extension == SQL_EXTENSION }.toList()

private fun codeLines(file: KoFileDeclaration): List<String> =
    file.text.lines().map { it.substringBefore(LINE_COMMENT) }
