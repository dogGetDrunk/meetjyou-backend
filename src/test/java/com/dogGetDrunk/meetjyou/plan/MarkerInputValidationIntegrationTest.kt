package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyKeyRepository
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles

/**
 * AT-001-2: marker request validation through the real API, validation, service and DB.
 * Violations must answer 400 INVALID_INPUT_VALUE and leave stored data untouched; boundary values
 * and an empty list must keep working. Assertions read the DB through repositories outside any
 * test transaction, so they never reuse the request's persistence context.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class MarkerInputValidationIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var planRepository: PlanRepository

    @Autowired
    private lateinit var markerRepository: MarkerRepository

    @Autowired
    private lateinit var idempotencyKeyRepository: IdempotencyKeyRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Value("\${local.server.port}")
    private var port: Int = 0

    private data class Violation(val name: String, val override: Map<String, Any?>)

    private data class Reply(val status: Int, val errorCode: String?)

    private data class MarkerSnapshot(
        val uuid: Any,
        val lat: Double,
        val lng: Double,
        val dayNum: Int,
        val idx: Int,
        val place: String,
        val memo: String?,
    )

    private val singleFieldViolations = listOf(
        Violation("memo 501 chars", mapOf("memo" to "m".repeat(MEMO_MAX + 1))),
        Violation("place 101 chars", mapOf("place" to "p".repeat(PLACE_MAX + 1))),
        Violation("blank place", mapOf("place" to "   ")),
        Violation("lat 90.000001", mapOf("lat" to 90.000001)),
        Violation("lng -180.000001", mapOf("lng" to -180.000001)),
        Violation("dayNum 0", mapOf("dayNum" to 0)),
        Violation("idx -1", mapOf("idx" to -1)),
    )

    private fun createUser(): User {
        val email = "marker-validation-${System.nanoTime()}@example.com"
        return userRepository.save(
            User(
                email = email,
                nickname = email.substringBefore("@").takeLast(NICKNAME_LEN),
                authProvider = AuthProvider.KAKAO,
                externalId = email,
            )
        )
    }

    private fun marker(override: Map<String, Any?> = emptyMap(), idx: Int = 0): Map<String, Any?> {
        val base = mapOf(
            "lat" to 37.5665,
            "lng" to 126.978,
            "date" to "2026-05-01T10:00:00Z",
            "dayNum" to 1,
            "idx" to idx,
            "place" to "Gyeongbokgung",
            "memo" to null,
        )
        return base + override
    }

    private fun markers(count: Int): List<Map<String, Any?>> = List(count) { marker(idx = it) }

    private fun planBody(markerList: List<Map<String, Any?>>): Map<String, Any?> = mapOf(
        "title" to "Seoul Trip",
        "itinStart" to "2026-05-01T00:00:00Z",
        "itinFinish" to "2026-05-05T00:00:00Z",
        "location" to "Seoul",
        "centerLat" to 37.5665,
        "centerLng" to 126.978,
        "memo" to null,
        "markers" to markerList,
    )

    private fun send(method: HttpMethod, path: String, body: Any, token: String): Reply {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }
        val response = restTemplate.exchange(
            "http://localhost:$port/api/v1$path",
            method,
            HttpEntity(objectMapper.writeValueAsString(body), headers),
            String::class.java,
        )
        val errorCode = response.body?.let { objectMapper.readTree(it).get("errorCode")?.asText() }
        return Reply(response.statusCode.value(), errorCode)
    }

    private fun createPlan(markerList: List<Map<String, Any?>>, token: String) =
        send(HttpMethod.POST, "/plans", planBody(markerList), token)

    private fun replaceMarkers(planUuid: Any, markerList: List<Map<String, Any?>>, token: String) =
        send(HttpMethod.PUT, "/plans/$planUuid/markers", mapOf("markers" to markerList), token)

    private fun snapshot(planUuid: java.util.UUID): List<MarkerSnapshot> =
        markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(planUuid).map {
            MarkerSnapshot(it.uuid, it.lat, it.lng, it.dayNum, it.idx, it.place, it.memo)
        }

    private fun seedPlanWithMarkers(owner: User): Plan {
        val plan = planRepository.save(
            Plan(
                title = "Existing",
                itinStart = java.time.Instant.parse("2026-05-01T00:00:00Z"),
                itinFinish = java.time.Instant.parse("2026-05-05T00:00:00Z"),
                destination = "Busan",
                centerLat = 35.1796,
                centerLng = 129.0756,
                owner = owner,
            )
        )
        markerRepository.saveAll(
            listOf(
                Marker(35.1, 129.0, java.time.Instant.parse("2026-05-01T09:00:00Z"), 1, 0, "Haeundae", "keep", plan),
                Marker(35.2, 129.1, java.time.Instant.parse("2026-05-02T09:00:00Z"), 2, 0, "Gwangalli", null, plan),
            )
        )
        return plan
    }

    private fun violatingLists(): List<Pair<String, List<Map<String, Any?>>>> {
        val fieldCases = singleFieldViolations.map { it.name to listOf(marker(it.override)) }
        return fieldCases + ("201 markers" to markers(MARKERS_MAX + 1))
    }

    private fun boundaryMarkers(): List<Map<String, Any?>> = listOf(
        marker(mapOf("lat" to 90.0, "lng" to 180.0, "memo" to "m".repeat(MEMO_MAX), "place" to "p".repeat(PLACE_MAX))),
        marker(mapOf("lat" to -90.0, "lng" to -180.0, "dayNum" to 1, "idx" to 1, "memo" to null)),
    )

    fun cleanup() {
        markerRepository.deleteAll()
        planRepository.deleteAll()
        idempotencyKeyRepository.deleteAll()
        userRepository.deleteAll()
    }

    init {
        extensions(SpringExtension())

        afterEach { cleanup() }

        given("AT-001-2 plan creation with a violating marker request") {
            `when`("each of the 8 violations is sent alone to POST /api/v1/plans") {
                then("every one is 400 INVALID_INPUT_VALUE and no plan or marker row is added") {
                    val user = createUser()
                    val token = jwtProvider.generateAccessToken(user.uuid, Role.USER)
                    val plansBefore = planRepository.count()
                    val markersBefore = markerRepository.count()

                    violatingLists().size shouldBe VIOLATION_COUNT
                    violatingLists().forEach { (name, list) ->
                        val reply = createPlan(list, token)
                        withClue(name) {
                            reply.status shouldBe 400
                            reply.errorCode shouldBe INVALID_INPUT_VALUE
                        }
                    }

                    planRepository.count() shouldBe plansBefore
                    markerRepository.count() shouldBe markersBefore
                }
            }
        }

        given("AT-001-2 marker replacement with a violating marker request") {
            `when`("each of the 8 violations is sent alone to PUT /api/v1/plans/{planUuid}/markers") {
                then("every one is 400 INVALID_INPUT_VALUE and the existing plan and markers stay intact") {
                    val owner = createUser()
                    val token = jwtProvider.generateAccessToken(owner.uuid, Role.USER)
                    val plan = seedPlanWithMarkers(owner)
                    val before = snapshot(plan.uuid)
                    val plansBefore = planRepository.count()
                    val markersBefore = markerRepository.count()

                    violatingLists().forEach { (name, list) ->
                        val reply = replaceMarkers(plan.uuid, list, token)
                        withClue(name) {
                            reply.status shouldBe 400
                            reply.errorCode shouldBe INVALID_INPUT_VALUE
                        }
                    }

                    snapshot(plan.uuid) shouldBe before
                    before.size shouldBe SEEDED_MARKER_COUNT
                    planRepository.count() shouldBe plansBefore
                    markerRepository.count() shouldBe markersBefore
                    planRepository.findByUuid(plan.uuid)?.title shouldBe "Existing"
                }
            }
        }

        given("AT-001-2 valid boundary requests") {
            `when`("200 markers are created and replaced") {
                then("creation succeeds and 200 markers are stored; replacement returns 200 with 200 markers") {
                    val owner = createUser()
                    val token = jwtProvider.generateAccessToken(owner.uuid, Role.USER)

                    val created = createPlan(markers(MARKERS_MAX), token)
                    created.status.isSuccess() shouldBe true
                    val createdPlan = planRepository.findAllByOwner_Uuid(owner.uuid, Pageable.unpaged())
                        .content.single()
                    snapshot(createdPlan.uuid).size shouldBe MARKERS_MAX

                    val seeded = seedPlanWithMarkers(owner)
                    replaceMarkers(seeded.uuid, markers(MARKERS_MAX), token).status shouldBe 200
                    snapshot(seeded.uuid).size shouldBe MARKERS_MAX
                }
            }

            `when`("memo 500, place 100, lat ±90, lng ±180, dayNum 1, idx 0 and null memo are sent") {
                then("create and replace both succeed and the boundary values are stored") {
                    val owner = createUser()
                    val token = jwtProvider.generateAccessToken(owner.uuid, Role.USER)

                    createPlan(boundaryMarkers(), token).status.isSuccess() shouldBe true
                    val createdPlan = planRepository.findAllByOwner_Uuid(owner.uuid, Pageable.unpaged())
                        .content.single()
                    assertBoundaryStored(snapshot(createdPlan.uuid))

                    val seeded = seedPlanWithMarkers(owner)
                    replaceMarkers(seeded.uuid, boundaryMarkers(), token).status shouldBe 200
                    assertBoundaryStored(snapshot(seeded.uuid))
                }
            }
        }

        given("AT-001-2 an empty marker list") {
            `when`("a plan is created and an existing plan's markers are replaced with []") {
                then("both are allowed and the replacement leaves no markers") {
                    val owner = createUser()
                    val token = jwtProvider.generateAccessToken(owner.uuid, Role.USER)

                    createPlan(emptyList(), token).status.isSuccess() shouldBe true
                    val createdPlan = planRepository.findAllByOwner_Uuid(owner.uuid, Pageable.unpaged())
                        .content.single()
                    snapshot(createdPlan.uuid).shouldBeEmpty()

                    val seeded = seedPlanWithMarkers(owner)
                    replaceMarkers(seeded.uuid, emptyList(), token).status shouldBe 200
                    snapshot(seeded.uuid).shouldBeEmpty()
                }
            }
        }
    }

    private fun assertBoundaryStored(stored: List<MarkerSnapshot>) {
        stored.size shouldBe 2
        val max = stored.single { it.lat == 90.0 }
        max.lng shouldBe 180.0
        max.memo shouldBe "m".repeat(MEMO_MAX)
        max.place shouldBe "p".repeat(PLACE_MAX)
        max.dayNum shouldBe 1
        max.idx shouldBe 0
        val min = stored.single { it.lat == -90.0 }
        min.lng shouldBe -180.0
        min.memo shouldBe null
    }

    private fun Int.isSuccess(): Boolean = this in HTTP_SUCCESS_RANGE

    private companion object {
        const val MARKERS_MAX = 200
        const val PLACE_MAX = 100
        const val MEMO_MAX = 500
        const val VIOLATION_COUNT = 8
        const val SEEDED_MARKER_COUNT = 2
        const val NICKNAME_LEN = 10
        const val INVALID_INPUT_VALUE = "INVALID_INPUT_VALUE"
        val HTTP_SUCCESS_RANGE = 200..299
    }
}
