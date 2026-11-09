package com.dogGetDrunk.meetjyou.place.importer

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.net.URLDecoder

// AT-007: request shapes are the ones the data.go.kr page sends (captured 2026-11-09).
class SbizFileClientTest : BehaviorSpec() {
    private lateinit var portal: MockRestServiceServer
    private lateinit var sut: SbizFileClient
    private val props = PlaceImportProperties(baseUrl = BASE)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun formOf(request: ClientHttpRequest): Map<String, String> =
        (request as MockClientHttpRequest).bodyAsString.split("&").associate {
            val (key, value) = it.split("=", limit = 2)
            key to URLDecoder.decode(value, Charsets.UTF_8)
        }

    private fun expectResolve(body: String, sent: MutableMap<String, String> = mutableMapOf()) {
        portal.expect(requestTo("$BASE${SbizFileClient.RESOLVE_PATH}"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect { sent.putAll(formOf(it)) }
            .andRespond(withSuccess(body, MediaType.TEXT_HTML))
    }

    private fun expectLimit(body: String, sent: MutableMap<String, String> = mutableMapOf()) {
        portal.expect(requestTo("$BASE${SbizFileClient.LIMIT_PATH}"))
            .andExpect(method(HttpMethod.POST))
            .andExpect { sent.putAll(formOf(it)) }
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    init {
        beforeEach {
            val builder = RestClient.builder().baseUrl(BASE)
            portal = MockRestServiceServer.bindTo(builder).build()
            sut = SbizFileClient(props, builder.build(), ObjectMapper())
        }

        given("세 단계가 모두 성공하면") {
            then("포털과 같은 파라미터로 조회·한도 확인·다운로드를 차례로 호출하고 본문을 넘긴다") {
                val resolveForm = mutableMapOf<String, String>()
                val limitForm = mutableMapOf<String, String>()
                expectResolve(RESOLVED, resolveForm)
                expectLimit("""{"needCaptcha":false}""", limitForm)
                portal.expect(requestTo("$BASE${SbizFileClient.DOWNLOAD_PATH}?atchFileId=FILE_1&fileDetailSn=1"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("zip-bytes".toByteArray(), MediaType.APPLICATION_OCTET_STREAM))

                val (name, body) = sut.download { file, input -> file.dataName to input.readBytes().decodeToString() }

                name shouldBe "소상공인시장진흥공단_상가(상권)정보_20260930"
                body shouldBe "zip-bytes"
                resolveForm shouldBe mapOf(
                    "publicDataPk" to "15083033",
                    "publicDataDetailPk" to "uddi:86f46f63-829a-46c1-abdd-d8e66846b6da",
                    "atchFileId" to "",
                    "fileDetailSn" to "1",
                    "publicDataTyCode" to "PR0051",
                )
                limitForm shouldBe mapOf("atchFileId" to "FILE_1", "fileDetailSn" to "1")
                portal.verify()
            }
        }

        given("파일 조회가 status=false면") {
            then("실패하고 이후 단계를 호출하지 않는다") {
                expectResolve("""{"status":false,"error":"no file"}""")

                shouldThrow<PlaceImportException> { sut.download { _, _ -> Unit } }
                portal.verify()
            }
        }

        given("한도 확인이 captcha를 요구하면") {
            then("실패하고 다운로드를 호출하지 않는다") {
                expectResolve(RESOLVED)
                expectLimit("""{"needCaptcha":true}""")

                shouldThrow<PlaceImportException> { sut.download { _, _ -> Unit } }
                portal.verify()
            }
        }

        given("한도 확인이 403이면") {
            then("실패하고 다운로드를 호출하지 않는다") {
                expectResolve(RESOLVED)
                portal.expect(requestTo("$BASE${SbizFileClient.LIMIT_PATH}"))
                    .andRespond(withStatus(HttpStatus.FORBIDDEN))

                shouldThrow<HttpClientErrorException.Forbidden> { sut.download { _, _ -> Unit } }
                portal.verify()
            }
        }

        given("다운로드가 오류 상태면") {
            then("본문을 넘기지 않고 실패한다") {
                expectResolve(RESOLVED)
                expectLimit("""{"needCaptcha":false}""")
                portal.expect(requestTo("$BASE${SbizFileClient.DOWNLOAD_PATH}?atchFileId=FILE_1&fileDetailSn=1"))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))
                var consumed = false

                shouldThrow<PlaceImportException> { sut.download { _, _ -> consumed = true } }
                consumed shouldBe false
            }
        }
    }

    private companion object {
        const val BASE = "https://portal.test"
        const val RESOLVED = """{"status":true,"atchFileId":"FILE_1","fileDetailSn":1,
            "dataSetFileDetailInfo":{"dataNm":"소상공인시장진흥공단_상가(상권)정보_20260930"}}"""
    }
}
