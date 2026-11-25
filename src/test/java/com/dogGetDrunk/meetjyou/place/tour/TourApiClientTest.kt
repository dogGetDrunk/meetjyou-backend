package com.dogGetDrunk.meetjyou.place.tour

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.SocketTimeoutException
import java.net.URI

/** AT-001: request shape, key encoding, item parsing and every failure as a key-free TourApiException. */
class TourApiClientTest : BehaviorSpec() {

    // Rebuilt per leaf: the mock server is stateful.
    private lateinit var server: MockRestServiceServer
    private lateinit var sut: TourApiClient
    private var sentUri: URI? = null

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun respond(body: String, type: MediaType = MediaType.APPLICATION_JSON) {
        server.expect { sentUri = it.uri }.andExpect(method(HttpMethod.GET)).andRespond(withSuccess(body, type))
    }

    private fun failure(block: () -> Unit): TourApiException {
        val e = shouldThrow<TourApiException> { block() }
        e.message.orEmpty() shouldNotContain KEY
        e.message.orEmpty() shouldNotContain ENCODED_KEY
        return e
    }

    init {
        beforeEach {
            val builder = RestClient.builder()
            server = MockRestServiceServer.bindTo(builder).build()
            val props = TourApiProperties(baseUrl = BASE_URL, serviceKey = KEY)
            sut = TourApiClient(props, builder.build(), ObjectMapper())
        }

        given("검색 요청") {
            then("필수 파라미터를 보내고 서비스키는 한 번만 인코딩한다") {
                respond(response("\"\""))
                sut.searchKeyword("경복궁", "11", null, 20)
                val uri = sentUri ?: error("no request")
                uri.path shouldBe "/B551011/KorService2/searchKeyword2"
                val query = uri.rawQuery
                query shouldContain "serviceKey=$ENCODED_KEY"
                query shouldContain "MobileOS=ETC"
                query shouldContain "MobileApp=meetjyou"
                query shouldContain "_type=json"
                query shouldContain "keyword=%EA%B2%BD%EB%B3%B5%EA%B6%81"
                query shouldContain "numOfRows=20"
                query shouldContain "pageNo=1"
                query shouldContain "lDongRegnCd=11"
                query shouldNotContain "lDongSignguCd"
            }
            then("시·군·구 코드가 있으면 시·도와 함께 보낸다") {
                respond(response("\"\""))
                sut.searchKeyword("박물관", "11", "110", 20)
                val query = (sentUri ?: error("no request")).rawQuery
                query shouldContain "lDongRegnCd=11"
                query shouldContain "lDongSignguCd=110"
            }
            then("시·도가 없으면 지역 파라미터를 보내지 않는다") {
                respond(response("\"\""))
                sut.searchKeyword("경복궁", null, null, 20)
                (sentUri ?: error("no request")).rawQuery shouldNotContain "lDongRegnCd"
            }
        }

        given("성공 응답") {
            then("item 배열을 읽는다") {
                respond(response("""{"item":[$ITEM,$ITEM]}"""))
                val places = sut.searchKeyword("경복궁", null, null, 20)
                places.size shouldBe 2
                places[0] shouldBe TourPlace("126508", "12", "경복궁", "서울특별시 종로구 사직로 161", "126.97", "37.57", "11", "110")
            }
            then("결과 1건이 배열이 아닌 객체로 와도 읽는다") {
                respond(response("""{"item":$ITEM}"""))
                sut.searchKeyword("경복궁", null, null, 20).size shouldBe 1
            }
            then("결과 0건의 빈 문자열 items는 빈 목록") {
                respond(response("\"\""))
                sut.searchKeyword("없는곳", null, null, 20).shouldBeEmpty()
            }
        }

        given("실패") {
            then("resultCode가 0000이 아니면 RESULT_CODE") {
                respond("""{"response":{"header":{"resultCode":"0003","resultMsg":"x"}}}""")
                failure { sut.searchKeyword("a", null, null, 1) }.reason shouldBe TourApiException.Reason.RESULT_CODE
            }
            then("코드 모양이 아닌 resultCode는 메시지에 옮기지 않는다") {
                respond("""{"response":{"header":{"resultCode":"$BASE_URL?serviceKey=$KEY"}}}""")
                val e = failure { sut.searchKeyword("a", null, null, 1) }
                e.reason shouldBe TourApiException.Reason.RESULT_CODE
                e.message.orEmpty() shouldNotContain "apis.example"
            }
            then("게이트웨이 XML 한도 초과(22)는 QUOTA_EXCEEDED와 사유 코드") {
                respond(gatewayXml("22"), MediaType.TEXT_XML)
                val e = failure { sut.searchKeyword("a", null, null, 1) }
                e.reason shouldBe TourApiException.Reason.QUOTA_EXCEEDED
                e.message.orEmpty() shouldContain "22"
            }
            then("JSON resultCode 22도 QUOTA_EXCEEDED") {
                respond("""{"response":{"header":{"resultCode":"22","resultMsg":"x"}}}""")
                failure { sut.searchKeyword("a", null, null, 1) }.reason shouldBe TourApiException.Reason.QUOTA_EXCEEDED
            }
            then("한도 초과가 아닌 게이트웨이 오류(미등록 키 30)는 RESULT_CODE") {
                respond(gatewayXml("30"), MediaType.TEXT_XML)
                failure { sut.searchKeyword("a", null, null, 1) }.reason shouldBe TourApiException.Reason.RESULT_CODE
            }
            then("JSON도 게이트웨이 XML도 아니면 BAD_BODY") {
                respond("<html>maintenance</html>", MediaType.TEXT_HTML)
                failure { sut.searchKeyword("a", null, null, 1) }.reason shouldBe TourApiException.Reason.BAD_BODY
            }
            then("2xx가 아니면 HTTP_STATUS") {
                server.expect { }.andRespond(withStatus(HttpStatus.BAD_GATEWAY))
                failure { sut.searchKeyword("a", null, null, 1) }.reason shouldBe TourApiException.Reason.HTTP_STATUS
            }
            then("타임아웃은 UNREACHABLE이고 메시지에 URI가 없다") {
                server.expect { }.andRespond(withException(SocketTimeoutException("Read timed out")))
                val e = failure { sut.searchKeyword("a", null, null, 1) }
                e.reason shouldBe TourApiException.Reason.UNREACHABLE
                e.message.orEmpty() shouldNotContain "apis.example"
            }
        }
    }

    private companion object {
        const val BASE_URL = "https://apis.example/B551011/KorService2"
        const val KEY = "ab+cd/ef=="
        const val ENCODED_KEY = "ab%2Bcd%2Fef%3D%3D"
        const val ITEM = """{"contentid":"126508","contenttypeid":"12","title":"경복궁",
            "addr1":"서울특별시 종로구 사직로 161","mapx":"126.97","mapy":"37.57","lDongRegnCd":"11","lDongSignguCd":"110",
            "firstimage":"http://img"}"""

        fun gatewayXml(code: String): String =
            """<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
            <returnAuthMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</returnAuthMsg>
            <returnReasonCode>$code</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>"""

        fun response(items: String): String =
            """{"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":$items,"numOfRows":20}}}"""
    }
}
