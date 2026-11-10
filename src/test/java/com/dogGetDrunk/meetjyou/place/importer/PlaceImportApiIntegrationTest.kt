package com.dogGetDrunk.meetjyou.place.importer

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.seconds

private const val H2_URL = "jdbc:h2:mem:place_import_api;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE"

/**
 * AT-006: admin guard, single-run guard (409) and the completion alerts. The writer is a mock: its
 * SQL is MySQL only and covered by PlaceImportDockerTest.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=$H2_URL"],
)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class, AuthenticationDetailsProvider::class, ObjectStorageClient::class, WorkRequestClient::class,
)
class PlaceImportApiIntegrationTest : BehaviorSpec() {
    @TestConfiguration
    class FakeSourceConfig {
        @Bean
        @Primary
        fun controllableSource() = ControllableSource()
    }

    /** Waits on [gate] (when set) and then serves [zip], or throws [failure]. */
    class ControllableSource : SbizFileSource {
        @Volatile var gate: CountDownLatch? = null
        @Volatile var failure: RuntimeException? = null

        override fun <T : Any> download(consumer: (SbizFileRef, InputStream) -> T): T {
            gate?.await(WAIT_SECONDS, TimeUnit.SECONDS)
            failure?.let { throw it }
            return consumer(SbizFileRef("FILE_1", "1", "fixture_20260930"), ByteArrayInputStream(oneStoreZip()))
        }
    }

    @Autowired private lateinit var rest: TestRestTemplate
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var source: ControllableSource
    @MockBean private lateinit var writer: PlaceImportWriter
    @MockBean private lateinit var discord: DiscordAlertService

    private fun user(role: Role): User {
        val tag = UUID.randomUUID().toString().take(10)
        return users.save(User(null, tag, AuthProvider.KAKAO, tag, role))
    }

    private fun call(method: HttpMethod, path: String, user: User): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.setBearerAuth(jwt.generateAccessToken(user.uuid, user.role))
        return rest.exchange("/api/v1/admin/places/imports$path", method, HttpEntity<Void>(headers), String::class.java)
    }

    private fun state(admin: User): String =
        mapper.readTree(call(HttpMethod.GET, "/latest", admin).body).get("state").asText()

    private fun alertSummaries(): List<String> = Mockito.mockingDetails(discord).invocations
        .filter { it.method.name == "sendOperationalAlert" && it.arguments[0] == PlaceImportService.ALERT_SOURCE }
        .map { it.arguments[2] as String }

    init {
        extensions(SpringExtension())

        beforeEach {
            source.gate = null
            source.failure = null
            Mockito.clearInvocations(discord)
            Mockito.`when`(writer.stagedCount()).thenReturn(1L)
            val counts = ApplyCounts(updated = 0, rematched = 0, inserted = 1, deactivated = 0)
            Mockito.`when`(writer.apply()).thenReturn(counts)
        }

        given("관리자가 아닌 사용자가") {
            then("적재 시작과 상태 조회 모두 403") {
                val member = user(Role.USER)
                call(HttpMethod.POST, "", member).statusCode shouldBe HttpStatus.FORBIDDEN
                call(HttpMethod.GET, "/latest", member).statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        given("적재가 실행 중일 때 관리자가 다시 시작하면") {
            then("두 번째 요청은 409, 끝나면 SUCCEEDED와 성공 알림") {
                val admin = user(Role.ADMIN)
                val gate = CountDownLatch(1)
                source.gate = gate

                val first = call(HttpMethod.POST, "", admin)
                first.statusCode shouldBe HttpStatus.ACCEPTED
                mapper.readTree(first.body).get("state").asText() shouldBe "RUNNING"
                val second = call(HttpMethod.POST, "", admin)
                second.statusCode shouldBe HttpStatus.CONFLICT
                mapper.readTree(second.body).get("errorCode").asText() shouldBe "PLACE_IMPORT_IN_PROGRESS"

                gate.countDown()
                eventually(WAIT_SECONDS.seconds) { state(admin) shouldBe "SUCCEEDED" }
                // The alert goes out after the status flips, so it may land a moment later.
                eventually(WAIT_SECONDS.seconds) { alertSummaries() shouldContain "Place import succeeded" }
                call(HttpMethod.POST, "", admin).statusCode shouldBe HttpStatus.ACCEPTED
                eventually(WAIT_SECONDS.seconds) { state(admin) shouldBe "SUCCEEDED" }
            }
        }

        given("다운로드가 실패하면") {
            then("FAILED, 실패 알림, 가드가 풀려 다시 시작할 수 있다") {
                val admin = user(Role.ADMIN)
                source.failure = PlaceImportException("portal changed")

                call(HttpMethod.POST, "", admin).statusCode shouldBe HttpStatus.ACCEPTED
                eventually(WAIT_SECONDS.seconds) { state(admin) shouldBe "FAILED" }
                eventually(WAIT_SECONDS.seconds) { alertSummaries() shouldContain "Place import failed" }
                Mockito.verify(writer, Mockito.never()).apply()

                source.failure = null
                call(HttpMethod.POST, "", admin).statusCode shouldBe HttpStatus.ACCEPTED
                eventually(WAIT_SECONDS.seconds) { state(admin) shouldBe "SUCCEEDED" }
            }
        }
    }

    companion object {
        private const val WAIT_SECONDS = 10L

        // One 카페 in 서울 강남구 (11680), the shape of the real file.
        fun oneStoreZip(): ByteArray {
            val header = SbizZipReader.REQUIRED_HEADERS.joinToString(",") { "\"$it\"" }
            val values = mapOf(
                SbizZipReader.SOURCE_KEY to "MA0001", SbizZipReader.NAME to "테스트카페", SbizZipReader.BRANCH to "",
                SbizZipReader.CATEGORY_CODE to "I21201", SbizZipReader.CATEGORY_NAME to "카페",
                SbizZipReader.SIDO_CODE to "11", SbizZipReader.SIGUNGU_CODE to "11680",
                SbizZipReader.ROAD_ADDRESS to "서울특별시 강남구 테헤란로 1",
                SbizZipReader.LNG to "127.0", SbizZipReader.LAT to "37.5",
            )
            val row = SbizZipReader.REQUIRED_HEADERS.joinToString(",") { "\"${values.getValue(it)}\"" }
            val out = ByteArrayOutputStream()
            ZipOutputStream(out, Charsets.UTF_8).use { zip ->
                zip.putNextEntry(ZipEntry("상가정보_서울_202609.csv"))
                zip.write("$header\n$row\n".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            return out.toByteArray()
        }
    }
}
