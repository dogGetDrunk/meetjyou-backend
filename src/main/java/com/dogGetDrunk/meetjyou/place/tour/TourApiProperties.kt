package com.dogGetDrunk.meetjyou.place.tour

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 한국관광공사 국문 관광정보 서비스(KorService2) on data.go.kr. Searched live, never stored (ADR-0004).
 * A blank [serviceKey] switches the tour search off, so a deploy without the key behaves as before.
 */
@ConfigurationProperties("place.tour")
data class TourApiProperties(
    val baseUrl: String = "https://apis.data.go.kr/B551011/KorService2",
    // The portal's "Decoding" key (may hold + / =); the client percent-encodes it exactly once.
    val serviceKey: String = "",
    val mobileApp: String = "meetjyou",
    val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    // Bounds each wait between reads, not the whole response: a response trickling in can run past it
    // (accepted, C-r1-02 in .pipeline/tasks/20261122-tour-api-realtime/decisions.md).
    val readTimeout: Duration = DEFAULT_READ_TIMEOUT,
) {
    val enabled: Boolean get() = serviceKey.isNotBlank()

    // The data class default would print the key into any log that prints the properties.
    override fun toString(): String {
        val key = if (enabled) MASK else ""
        val fields = listOf(
            "baseUrl=$baseUrl",
            "serviceKey=$key",
            "mobileApp=$mobileApp",
            "connectTimeout=$connectTimeout",
            "readTimeout=$readTimeout",
        )
        return "TourApiProperties(${fields.joinToString(", ")})"
    }

    private companion object {
        const val MASK = "***"
        val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(1)
        val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(2)
    }
}
