package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.place.support.CountingTourConfig
import com.dogGetDrunk.meetjyou.place.support.CountingTourSource
import com.dogGetDrunk.meetjyou.plan.dto.GetPlanResponse
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val GYEONGBOKGUNG = "126508"
private const val BULGUKSA = "126207"

/**
 * AT-003 (관광공사 contentid on post / party / plan: writes, every response, source switches, raw JSON
 * tokens, no tour call) and AT-004 (plan snapshots written before the field still deserialize).
 */
// Same context settings as TravelDestinationApiIntegrationTest on purpose: one cached context for both
// (each extra one costs heap in the shared test JVM).
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=$TRAVEL_DESTINATION_H2_URL", "spring.jpa.properties.hibernate.generate_statistics=true",
    ],
)
@ActiveProfiles("test")
@Import(CountingTourConfig::class)
@MockBean(
    FirebaseApp::class, AuthenticationDetailsProvider::class, ObjectStorageClient::class, WorkRequestClient::class,
)
class TourContentRefApiIntegrationTest : BehaviorSpec() {
    @Autowired private lateinit var rest: TestRestTemplate
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var places: PlaceRepository
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var tourSource: CountingTourSource

    private fun user(role: Role = Role.USER): User {
        val tag = UUID.randomUUID().toString().take(10)
        return users.save(User(null, tag, AuthProvider.KAKAO, tag, role))
    }

    // Party lookup by uuid is an admin endpoint.
    private val admin by lazy { user(Role.ADMIN) }

    private fun store(regionCode: String): Place =
        places.save(
            Place(
                source = PlaceSource.SBIZ, sourceKey = UUID.randomUUID().toString().take(20), name = "카페",
                branchName = null, searchName = "카페", categoryCode = "I21201", categoryName = "카페",
                regionCode = regionCode, roadAddress = "도로명 1", lat = 37.0, lng = 127.0, active = true,
            ),
        )

    private fun call(method: HttpMethod, path: String, user: User, body: Any? = null): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(jwt.generateAccessToken(user.uuid, user.role))
        return rest.exchange("/api/v1$path", method, HttpEntity(body, headers), String::class.java)
    }

    private fun ok(response: ResponseEntity<String>): JsonNode {
        response.statusCode.is2xxSuccessful shouldBe true
        return mapper.readTree(response.body)
    }

    /** [destination] holds only the keys to send: a missing key and an explicit null are different bodies. */
    private fun postBody(destination: Map<String, Any?>): Map<String, Any?> {
        val start = Instant.now().plus(3, ChronoUnit.DAYS)
        return mapOf(
            "title" to "제목", "content" to "내용", "isInstant" to false, "itinStart" to start,
            "itinFinish" to start.plus(1, ChronoUnit.DAYS), "location" to "경복궁", "capacity" to 4,
            "companionSpec" to null, "planUuid" to null, "isPlanPublic" to null,
        ) + destination
    }

    private fun planBody(destination: Map<String, Any?>): Map<String, Any?> {
        val start = Instant.now().plus(3, ChronoUnit.DAYS)
        return mapOf(
            "title" to "계획", "itinStart" to start, "itinFinish" to start.plus(1, ChronoUnit.DAYS),
            "location" to "경복궁", "centerLat" to 37.0, "centerLng" to 127.0, "memo" to null, "favorite" to false,
        ) + destination
    }

    private fun tour(id: Any?, regionCode: String? = "11110"): Map<String, Any?> =
        mapOf("regionCode" to regionCode, "tourContentId" to id)

    private fun storePlace(place: Place): Map<String, Any?> = mapOf("placeId" to place.id)

    private fun getPost(user: User, uuid: String): JsonNode = ok(call(HttpMethod.GET, "/posts/$uuid", user))

    private fun getParty(post: JsonNode): JsonNode =
        ok(call(HttpMethod.GET, "/parties/${post.get("partyUuid").asText()}", admin))

    private fun tourIdOf(node: JsonNode): String? {
        // The field is always present; null is how a store or region-only destination shows.
        node.has("tourContentId") shouldBe true
        val value = node.get("tourContentId")
        return if (value.isNull) null else value.asText()
    }

    private fun placeIdOf(node: JsonNode): Long? {
        val place = node.get("place")
        return if (place == null || place.isNull) null else place.get("id").asLong()
    }

    init {
        extensions(SpringExtension())

        given("관광공사 장소로 모집글을 만들면") {
            then("생성, 조회, 파티, 내 파티 목록, 파티 이름 변경 응답 모두 같은 id") {
                val toursBefore = tourSource.calls
                val author = user()
                val created = ok(call(HttpMethod.POST, "/posts", author, postBody(tour(GYEONGBOKGUNG))))
                tourIdOf(created) shouldBe GYEONGBOKGUNG
                placeIdOf(created) shouldBe null

                val read = getPost(author, created.get("uuid").asText())
                tourIdOf(read) shouldBe GYEONGBOKGUNG
                read.get("region").get("code").asText() shouldBe "11110"

                val party = getParty(read)
                tourIdOf(party) shouldBe GYEONGBOKGUNG
                placeIdOf(party) shouldBe null

                val partyUuid = read.get("partyUuid").asText()
                val mine = ok(call(HttpMethod.GET, "/users/me/parties", author)).get("content")
                    .first { it.get("partyUuid").asText() == partyUuid }
                tourIdOf(mine) shouldBe GYEONGBOKGUNG

                val renamed = ok(call(HttpMethod.PATCH, "/parties/$partyUuid", author, mapOf("name" to "새 이름")))
                tourIdOf(renamed) shouldBe GYEONGBOKGUNG
                tourSource.calls shouldBe toursBefore
            }
        }

        given("모집글 수정으로 장소 출처를 바꾸면") {
            then("store → 관광공사 → store → 없음, 매번 글과 파티가 같이 바뀌고 이전 출처는 비워진다") {
                val toursBefore = tourSource.calls
                val author = user()
                val cafe = store("11110")
                val created = ok(call(HttpMethod.POST, "/posts", author, postBody(storePlace(cafe))))
                val uuid = created.get("uuid").asText()
                placeIdOf(created) shouldBe cafe.id
                tourIdOf(created) shouldBe null

                val toTour = ok(call(HttpMethod.PUT, "/posts/$uuid", author, postBody(tour(BULGUKSA))))
                tourIdOf(toTour) shouldBe BULGUKSA
                placeIdOf(toTour) shouldBe null
                val partyAfterTour = getParty(getPost(author, uuid))
                tourIdOf(partyAfterTour) shouldBe BULGUKSA
                placeIdOf(partyAfterTour) shouldBe null

                ok(call(HttpMethod.PUT, "/posts/$uuid", author, postBody(storePlace(cafe))))
                val backToStore = getPost(author, uuid)
                tourIdOf(backToStore) shouldBe null
                placeIdOf(backToStore) shouldBe cafe.id
                tourIdOf(getParty(backToStore)) shouldBe null
                placeIdOf(getParty(backToStore)) shouldBe cafe.id

                ok(call(HttpMethod.PUT, "/posts/$uuid", author, postBody(tour(BULGUKSA))))
                ok(call(HttpMethod.PUT, "/posts/$uuid", author, postBody(emptyMap())))
                val cleared = getPost(author, uuid)
                tourIdOf(cleared) shouldBe null
                tourIdOf(getParty(cleared)) shouldBe null
                tourSource.calls shouldBe toursBefore
            }
        }

        given("여행계획서") {
            then("생성, 수정, 조회 응답에 id가 실리고 store와 서로 바꿀 수 있다") {
                val toursBefore = tourSource.calls
                val owner = user()
                val cafe = store("11110")
                val created = ok(call(HttpMethod.POST, "/plans", owner, planBody(tour(GYEONGBOKGUNG))))
                tourIdOf(created) shouldBe GYEONGBOKGUNG
                val uuid = created.get("uuid").asText()

                val updated = ok(call(HttpMethod.PUT, "/plans/$uuid", owner, planBody(tour(BULGUKSA))))
                tourIdOf(updated) shouldBe BULGUKSA
                tourIdOf(ok(call(HttpMethod.GET, "/plans/$uuid", owner))) shouldBe BULGUKSA

                val toStore = ok(call(HttpMethod.PUT, "/plans/$uuid", owner, planBody(storePlace(cafe))))
                tourIdOf(toStore) shouldBe null
                placeIdOf(toStore) shouldBe cafe.id

                ok(call(HttpMethod.PUT, "/plans/$uuid", owner, planBody(tour(GYEONGBOKGUNG))))
                val toTour = ok(call(HttpMethod.GET, "/plans/$uuid", owner))
                tourIdOf(toTour) shouldBe GYEONGBOKGUNG
                placeIdOf(toTour) shouldBe null
                tourSource.calls shouldBe toursBefore
            }
        }

        given("잘못된 관광공사 장소 요청") {
            then("store와 함께 보내거나 형식이 틀리면 400") {
                val toursBefore = tourSource.calls
                val author = user()
                val cafe = store("11110")
                val both = mapOf("placeId" to cafe.id, "tourContentId" to GYEONGBOKGUNG)
                call(HttpMethod.POST, "/posts", author, postBody(both)).statusCode shouldBe HttpStatus.BAD_REQUEST
                call(HttpMethod.POST, "/plans", author, planBody(both)).statusCode shouldBe HttpStatus.BAD_REQUEST
                listOf("12a", "", "1".repeat(TravelDestination.TOUR_CONTENT_ID_MAX_LENGTH + 1)).forEach {
                    call(HttpMethod.POST, "/posts", author, postBody(tour(it))).statusCode shouldBe
                        HttpStatus.BAD_REQUEST
                }
                tourSource.calls shouldBe toursBefore
            }
        }

        given("JSON 토큰 종류") {
            then("따옴표 없는 정수는 문자열로 저장, 명시적 null은 생략과 같다") {
                val toursBefore = tourSource.calls
                val author = user()
                val numeric = ok(call(HttpMethod.POST, "/posts", author, postBody(tour(126508))))
                tourIdOf(getPost(author, numeric.get("uuid").asText())) shouldBe GYEONGBOKGUNG

                val explicitNull = ok(call(HttpMethod.POST, "/posts", author, postBody(tour(null))))
                tourIdOf(getPost(author, explicitNull.get("uuid").asText())) shouldBe null
                tourSource.calls shouldBe toursBefore
            }
        }

        // AT-004
        given("필드가 생기기 전에 저장된 planSnapshot JSON") {
            then("앱 ObjectMapper로 예외 없이 읽히고 id는 null") {
                val legacy = """
                    {"uuid":"${UUID.randomUUID()}","title":"계획","itinStart":"2026-01-01T00:00:00Z",
                     "itinFinish":"2026-01-02T00:00:00Z","destination":"경복궁","centerLat":37.0,"centerLng":127.0,
                     "memo":null,"favorite":false,"userUuid":"${UUID.randomUUID()}","markers":[],
                     "region":null,"place":null}
                """.trimIndent()
                mapper.readValue(legacy, GetPlanResponse::class.java).tourContentId shouldBe null
            }
        }
    }
}
