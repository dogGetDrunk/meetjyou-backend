package com.dogGetDrunk.meetjyou.place.tour

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/**
 * `searchKeyword2` of the 국문 관광정보 서비스, written from the data.go.kr Swagger (15101578) without a
 * live call: the item fields and the empty-result shape are as documented there (ADR-0004, task
 * 20261122-tour-api-realtime). Every failure becomes a [TourApiException] whose message holds no key.
 */
class TourApiClient(
    private val props: TourApiProperties,
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
) : TourPlaceSource {
    override val enabled: Boolean get() = props.enabled

    override fun searchKeyword(keyword: String, sidoCode: String?, size: Int): List<TourPlace> {
        val body = fetch(buildUri(keyword, sidoCode, size))
        val response = parse(body).path("response")
        val resultCode = response.path("header").path("resultCode").asText("")
        if (resultCode != SUCCESS_CODE) {
            // Upstream text goes into logs: only a short code-shaped value is echoed (V-r1-01).
            val shown = resultCode.takeIf { RESULT_CODE_SHAPE.matches(it) } ?: UNSHOWN_CODE
            throw TourApiException(TourApiException.Reason.RESULT_CODE, "resultCode=$shown")
        }
        return items(response.path("body").path("items").path("item")).map(::toTourPlace)
    }

    // Values go in as URI variables: those are encoded strictly, so the key's '+' becomes %2B exactly once.
    // A literal query value would keep '+', which the gateway reads as a space (= wrong key).
    private fun buildUri(keyword: String, sidoCode: String?, size: Int): URI {
        val values = mutableMapOf(
            "serviceKey" to props.serviceKey,
            "MobileOS" to MOBILE_OS,
            "MobileApp" to props.mobileApp,
            "_type" to RESPONSE_TYPE,
            "keyword" to keyword,
            "numOfRows" to size.toString(),
            "pageNo" to FIRST_PAGE,
        )
        if (sidoCode != null) values["lDongRegnCd"] = sidoCode
        val builder = UriComponentsBuilder.fromUriString(props.baseUrl).path(SEARCH_KEYWORD_PATH)
        values.keys.forEach { builder.queryParam(it, "{$it}") }
        return builder.encode().buildAndExpand(values).toUri()
    }

    // Spring's exception messages hold the request URI (with the key), so neither the message nor the
    // cause is kept: only the class name.
    @Suppress("SwallowedException")
    private fun fetch(uri: URI): String {
        val body = try {
            restClient.get().uri(uri).exchange { _, response -> readBody(response) }
        } catch (e: RestClientException) {
            throw TourApiException(TourApiException.Reason.UNREACHABLE, e.javaClass.simpleName)
        }
        return body ?: throw TourApiException(TourApiException.Reason.BAD_BODY, "empty body")
    }

    private fun readBody(response: ClientHttpResponse): String {
        if (!response.statusCode.is2xxSuccessful) {
            throw TourApiException(TourApiException.Reason.HTTP_STATUS, "status=${response.statusCode.value()}")
        }
        return response.body.readAllBytes().toString(Charsets.UTF_8)
    }

    // The data.go.kr gateway answers its own errors (quota exceeded = 22, unregistered key = 30) in XML
    // even when JSON was asked for. A body that is neither may echo the request, so it is not kept.
    @Suppress("SwallowedException")
    private fun parse(body: String): JsonNode = try {
        objectMapper.readTree(body)
    } catch (e: JsonProcessingException) {
        val gatewayCode = GATEWAY_REASON.find(body)?.groupValues?.get(1)
        if (gatewayCode != null) {
            throw TourApiException(TourApiException.Reason.RESULT_CODE, "gatewayReasonCode=$gatewayCode")
        }
        throw TourApiException(TourApiException.Reason.BAD_BODY, e.javaClass.simpleName)
    }

    // No result: `items` is an empty string, so `item` is missing. One result may come as a bare object.
    private fun items(item: JsonNode): List<JsonNode> = when {
        item.isArray -> item.toList()
        item.isObject -> listOf(item)
        else -> emptyList()
    }

    private fun toTourPlace(item: JsonNode): TourPlace = TourPlace(
        contentId = item.path("contentid").asText(""),
        contentTypeId = item.path("contenttypeid").asText(""),
        title = item.path("title").asText(""),
        address = item.path("addr1").asText(""),
        mapx = item.path("mapx").asText(""),
        mapy = item.path("mapy").asText(""),
        lDongRegnCd = item.path("lDongRegnCd").asText(""),
        lDongSignguCd = item.path("lDongSignguCd").asText(""),
    )

    companion object {
        const val SEARCH_KEYWORD_PATH = "/searchKeyword2"
        const val SUCCESS_CODE = "0000"
        private const val MOBILE_OS = "ETC"
        private const val RESPONSE_TYPE = "json"
        private const val FIRST_PAGE = "1"
        private const val UNSHOWN_CODE = "<not a code>"
        private val RESULT_CODE_SHAPE = Regex("[A-Za-z0-9_]{1,10}")
        private val GATEWAY_REASON = Regex("<returnReasonCode>\\s*(\\d+)\\s*</returnReasonCode>")
    }
}
