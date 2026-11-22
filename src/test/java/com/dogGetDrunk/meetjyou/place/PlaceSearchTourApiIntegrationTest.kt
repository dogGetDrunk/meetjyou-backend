package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.place.tour.TourApiException
import com.dogGetDrunk.meetjyou.place.tour.TourPlace
import com.dogGetDrunk.meetjyou.place.tour.TourPlaceSource
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
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.util.UUID
import javax.sql.DataSource

private const val H2_URL = "jdbc:h2:mem:place_search_tour_api;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE"

/**
 * AT-003 (tour failure keeps the search 200), AT-004 (tour call outside any transaction, no pooled
 * connection held: fails with Boot's default open-in-view, see OpenInViewConfig) and AT-005 (response
 * shape). The query "asia" has no indexable token, so the MySQL-only full-text path never runs on H2.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=$H2_URL"],
)
@ActiveProfiles("test")
@Import(PlaceSearchTourApiIntegrationTest.FakeTourConfig::class)
@MockBean(
    FirebaseApp::class, AuthenticationDetailsProvider::class, ObjectStorageClient::class, WorkRequestClient::class,
)
class PlaceSearchTourApiIntegrationTest : BehaviorSpec() {
    @Autowired private lateinit var rest: TestRestTemplate
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var fake: FakeTourSource
    @Autowired private lateinit var handlerMapping: RequestMappingHandlerMapping

    class FakeTourSource : TourPlaceSource {
        override val enabled = true
        var fail = false
        var calls = 0
        var transactionActive: Boolean? = null
        var activeConnections: Int? = null
        lateinit var pool: HikariDataSource

        override fun searchKeyword(keyword: String, sidoCode: String?, size: Int): List<TourPlace> {
            calls++
            transactionActive = TransactionSynchronizationManager.isActualTransactionActive()
            activeConnections = pool.hikariPoolMXBean.activeConnections
            if (fail) throw TourApiException(TourApiException.Reason.RESULT_CODE, "gatewayReasonCode=22")
            return listOf(TourPlace("126508", "12", "경복궁", "서울특별시 종로구 사직로 161", "126.97", "37.57", "11", "110"))
        }
    }

    @TestConfiguration
    class FakeTourConfig {
        @Bean
        @Primary
        fun fakeTourSource(dataSource: DataSource): FakeTourSource {
            val source = FakeTourSource()
            source.pool = dataSource.unwrap(HikariDataSource::class.java)
            return source
        }
    }

    private val member by lazy {
        val tag = UUID.randomUUID().toString().take(10)
        users.save(User(null, tag, AuthProvider.KAKAO, tag, Role.USER))
    }

    private fun search(query: String): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.setBearerAuth(jwt.generateAccessToken(member.uuid, member.role))
        return rest.exchange("/api/v1/places/search?$query", HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
    }

    private fun json(response: ResponseEntity<String>): JsonNode = mapper.readTree(response.body)

    // OpenInViewConfig is the only web-request interceptor, so its adapter count tells whether it applies.
    private fun openInViewInterceptors(path: String): Int {
        val chain = handlerMapping.getHandler(MockHttpServletRequest("GET", path)) ?: error("no handler for $path")
        return chain.interceptorList.count { it is WebRequestHandlerInterceptorAdapter }
    }

    init {
        extensions(SpringExtension())

        beforeEach {
            fake.fail = false
            fake.calls = 0
            fake.transactionActive = null
            fake.activeConnections = null
        }

        given("관광공사가 응답하면") {
            then("tourPlaces와 tourStatus=OK가 오고, 호출 중에는 트랜잭션도 커넥션도 없다") {
                val response = search("q=asia")
                response.statusCode shouldBe HttpStatus.OK
                val body = json(response)
                body["tourStatus"].asText() shouldBe "OK"
                val item = body["tourPlaces"][0]
                item["contentId"].asText() shouldBe "126508"
                item["name"].asText() shouldBe "경복궁"
                item["categoryName"].asText() shouldBe "관광지"
                item["address"].asText() shouldBe "서울특별시 종로구 사직로 161"
                item["lat"].asDouble() shouldBe 37.57
                item["lng"].asDouble() shouldBe 126.97
                item["region"]["code"].asText() shouldBe "11110"
                body["regions"].isArray shouldBe true
                body["places"].isArray shouldBe true
                fake.transactionActive shouldBe false
                fake.activeConnections shouldBe 0
            }
        }

        given("관광공사가 실패하면") {
            then("200, 지역과 가게 묶음은 그대로, tourPlaces는 비고 FAILED") {
                fake.fail = true
                val response = search("q=asia")
                response.statusCode shouldBe HttpStatus.OK
                val body = json(response)
                body["tourStatus"].asText() shouldBe "FAILED"
                body["tourPlaces"].size() shouldBe 0
                body["regions"].isArray shouldBe true
                body["places"].isArray shouldBe true
            }
        }

        given("open-in-view 인터셉터") {
            then("검색 경로만 빠지고 다른 경로에는 그대로 걸린다") {
                openInViewInterceptors("/api/v1/places/search") shouldBe 0
                openInViewInterceptors("/api/v1/notices") shouldBe 1
            }
        }

        given("입력 검증에 실패하면") {
            then("400이고 관광공사를 호출하지 않는다") {
                search("q=a").statusCode shouldBe HttpStatus.BAD_REQUEST
                search("q=asia&region=99999").statusCode shouldBe HttpStatus.BAD_REQUEST
                fake.calls shouldBe 0
            }
        }
    }
}
