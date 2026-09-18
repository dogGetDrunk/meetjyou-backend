package com.dogGetDrunk.meetjyou.convention

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import io.kotest.core.spec.style.BehaviorSpec

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

private fun codeLines(file: KoFileDeclaration): List<String> =
    file.text.lines().map { it.substringBefore(LINE_COMMENT) }
