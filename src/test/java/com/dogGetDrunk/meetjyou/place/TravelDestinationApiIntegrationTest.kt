package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
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
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
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

private const val H2_URL = "jdbc:h2:mem:travel_destination_api;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE"

/** AT-011 (post/party/plan destinations) and AT-009 (search parameter validation). */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=$H2_URL", "spring.jpa.properties.hibernate.generate_statistics=true"],
)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class, AuthenticationDetailsProvider::class, ObjectStorageClient::class, WorkRequestClient::class,
)
class TravelDestinationApiIntegrationTest : BehaviorSpec() {
    @Autowired private lateinit var rest: TestRestTemplate
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var places: PlaceRepository
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var entityManagerFactory: EntityManagerFactory

    private fun user(role: Role = Role.USER): User {
        val tag = UUID.randomUUID().toString().take(10)
        return users.save(User(null, tag, AuthProvider.KAKAO, tag, role))
    }

    // Party lookup by uuid is an admin endpoint.
    private val admin by lazy { user(Role.ADMIN) }

    private fun place(regionCode: String, active: Boolean = true, name: String = "카페"): Place =
        places.save(
            Place(
                source = PlaceSource.SBIZ, sourceKey = UUID.randomUUID().toString().take(20), name = name,
                branchName = null, searchName = name, categoryCode = "I21201", categoryName = "카페",
                regionCode = regionCode, roadAddress = "도로명 1", lat = 37.0, lng = 127.0, active = active,
            ),
        )

    private fun call(method: HttpMethod, path: String, user: User, body: Any? = null): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(jwt.generateAccessToken(user.uuid, user.role))
        return rest.exchange("/api/v1$path", method, HttpEntity(body, headers), String::class.java)
    }

    private fun json(response: ResponseEntity<String>): JsonNode = mapper.readTree(response.body)

    private fun postBody(regionCode: String?, placeId: Long?): Map<String, Any?> {
        val start = Instant.now().plus(3, ChronoUnit.DAYS)
        return mapOf(
            "title" to "제목", "content" to "내용", "isInstant" to false, "itinStart" to start,
            "itinFinish" to start.plus(1, ChronoUnit.DAYS), "location" to "자유 입력", "capacity" to 4,
            "companionSpec" to null, "planUuid" to null, "isPlanPublic" to null,
            "regionCode" to regionCode, "placeId" to placeId,
        )
    }

    private fun planBody(regionCode: String?, placeId: Long?): Map<String, Any?> {
        val start = Instant.now().plus(3, ChronoUnit.DAYS)
        return mapOf(
            "title" to "계획", "itinStart" to start, "itinFinish" to start.plus(1, ChronoUnit.DAYS),
            "location" to "자유 입력", "centerLat" to 37.0, "centerLng" to 127.0, "memo" to null, "favorite" to false,
            "regionCode" to regionCode, "placeId" to placeId,
        )
    }

    private fun createPost(author: User, regionCode: String?, placeId: Long?): JsonNode {
        val response = call(HttpMethod.POST, "/posts", author, postBody(regionCode, placeId))
        response.statusCode.is2xxSuccessful shouldBe true
        return json(response)
    }

    private fun party(user: User, post: JsonNode): JsonNode {
        val detail = json(call(HttpMethod.GET, "/posts/${post.get("uuid").asText()}", user))
        return json(call(HttpMethod.GET, "/parties/${detail.get("partyUuid").asText()}", admin))
    }

    private fun statementCount(block: () -> Unit): Long {
        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        val before = statistics.prepareStatementCount
        block()
        return statistics.prepareStatementCount - before
    }

    init {
        extensions(SpringExtension())

        given("모집글 생성") {
            then("장소만 주면 장소의 일반구가 지역이 되고, 상위 시·시·도가 응답에 실린다") {
                val author = user()
                val cafe = place("41111")
                val post = createPost(author, null, cafe.id)
                val region = post.get("region")
                region.get("code").asText() shouldBe "41111"
                region.get("name").asText() shouldBe "수원시 장안구"
                region.get("fullName").asText() shouldBe "경기도 수원시 장안구"
                region.get("level").asText() shouldBe "SIGUNGU"
                region.get("sidoCode").asText() shouldBe "41"
                region.get("sidoName").asText() shouldBe "경기도"
                region.get("cityCode").asText() shouldBe "41110"
                region.get("cityName").asText() shouldBe "수원시"
                post.get("place").get("id").asLong() shouldBe cafe.id
                post.get("place").get("active").asBoolean() shouldBe true
                party(author, post).get("region").get("code").asText() shouldBe "41111"
                party(author, post).get("place").get("id").asLong() shouldBe cafe.id
            }
            then("시·도와 그 안의 장소를 함께 주면 요청한 시·도가 지역이 된다") {
                val author = user()
                val post = createPost(author, "41", place("41111").id)
                val region = post.get("region")
                region.get("code").asText() shouldBe "41"
                region.get("level").asText() shouldBe "SIDO"
                region.get("sidoCode").asText() shouldBe "41"
                region.get("cityCode").isNull shouldBe true
            }
            then("참조 없이 기존 필드만 보내면 미지정으로 저장된다") {
                val author = user()
                val post = createPost(author, null, null)
                post.get("location").asText() shouldBe "자유 입력"
                post.get("region").get("code").asText() shouldBe "00"
                post.get("region").get("name").asText() shouldBe "미지정"
                post.get("region").get("sidoCode").asText() shouldBe "00"
                post.get("place").isNull shouldBe true
                party(author, post).get("region").get("code").asText() shouldBe "00"
            }
            then("지역 불일치·없는 지역·미지정 지정·없는 장소는 400") {
                val author = user()
                val janganCafe = place("41111")
                val invalid = listOf("11680" to janganCafe.id, "99999" to null, "00" to null, null to Long.MAX_VALUE)
                invalid.forEach { (regionCode, placeId) ->
                    val response = call(HttpMethod.POST, "/posts", author, postBody(regionCode, placeId))
                    response.statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }
            then("비활성 장소를 참조한 글은 place.active=false로 조회된다") {
                val author = user()
                val closed = place("11680", active = false)
                val post = createPost(author, null, closed.id)
                val read = json(call(HttpMethod.GET, "/posts/${post.get("uuid").asText()}", author))
                read.get("place").get("active").asBoolean() shouldBe false
            }
        }

        given("모집글 수정") {
            then("지역을 바꾸면 글과 파티가 함께 바뀌고, 참조를 빼면 미지정이 된다") {
                val author = user()
                val post = createPost(author, null, place("41111").id)
                val uuid = post.get("uuid").asText()

                val moved = call(HttpMethod.PUT, "/posts/$uuid", author, postBody("11680", null))
                moved.statusCode.is2xxSuccessful shouldBe true
                json(moved).get("region").get("code").asText() shouldBe "11680"
                json(moved).get("place").isNull shouldBe true
                party(author, post).get("region").get("code").asText() shouldBe "11680"

                val cleared = call(HttpMethod.PUT, "/posts/$uuid", author, postBody(null, null))
                json(cleared).get("region").get("code").asText() shouldBe "00"
                party(author, post).get("region").get("code").asText() shouldBe "00"
            }
        }

        given("여행계획서 생성·수정·조회") {
            then("지역·장소가 저장되고 조회 응답에 실린다") {
                val owner = user()
                val cafe = place("11680")
                val created = call(HttpMethod.POST, "/plans", owner, planBody("11", cafe.id))
                created.statusCode.is2xxSuccessful shouldBe true
                json(created).get("region").get("code").asText() shouldBe "11"
                val uuid = json(created).get("uuid").asText()

                val updated = call(HttpMethod.PUT, "/plans/$uuid", owner, planBody(null, cafe.id))
                json(updated).get("region").get("code").asText() shouldBe "11680"
                val read = json(call(HttpMethod.GET, "/plans/$uuid", owner))
                read.get("region").get("code").asText() shouldBe "11680"
                read.get("place").get("id").asLong() shouldBe cafe.id

                val mismatch = call(HttpMethod.PUT, "/plans/$uuid", owner, planBody("41", cafe.id))
                mismatch.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }

        given("작성자별 모집글 목록") {
            then("서로 다른 지역·장소를 가진 글이 2건이든 10건이든 SQL 문 수가 같다") {
                val districts = listOf(
                    "11680", "41111", "41113", "26110", "27110", "28177", "12210", "30110", "31110", "50110",
                )
                val few = user()
                val many = user()
                districts.take(2).forEach { createPost(few, null, place(it).id) }
                districts.forEach { createPost(many, null, place(it).id) }
                val reader = user()
                // Warm-up: loads the region catalog and the reader's auth cache entry once.
                call(HttpMethod.GET, "/posts/author/${few.uuid}?size=20", reader)

                val fewCount = statementCount { call(HttpMethod.GET, "/posts/author/${few.uuid}?size=20", reader) }
                val manyCount = statementCount {
                    val response = call(HttpMethod.GET, "/posts/author/${many.uuid}?size=20", reader)
                    json(response).get("content").size() shouldBe districts.size
                }
                manyCount shouldBe fewCount
            }
        }

        given("여행지 검색 파라미터가 잘못되면") {
            then("400") {
                val member = user()
                listOf(
                    "q=a",
                    "q=${"가".repeat(51)}",
                    "q=카페&size=0",
                    "q=카페&size=51",
                    "q=카페&region=99999",
                    "q=카페&region=00",
                ).forEach { query ->
                    call(HttpMethod.GET, "/places/search?$query", member).statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }
    }
}
