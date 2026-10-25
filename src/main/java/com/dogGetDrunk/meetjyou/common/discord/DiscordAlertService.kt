package com.dogGetDrunk.meetjyou.common.discord

import com.dogGetDrunk.meetjyou.config.DiscordProperties
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture

@Service
class DiscordAlertService(props: DiscordProperties) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = if (props.webhookUrl.isNotBlank()) RestClient.create(props.webhookUrl) else null
    private val dedupCache: Cache<String, Boolean> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(props.deduplicationWindowSeconds))
        .maximumSize(200)
        .build()

    fun sendAlert(
        request: HttpServletRequest,
        status: Int,
        exceptionClass: String,
        summary: String,
        detail: String? = null
    ) {
        if (status < SERVER_ERROR_MIN_STATUS) {
            log.debug("Skipping Discord alert for client error: {} {}", status, exceptionClass)
            return
        }
        deliver(request.location(), status, exceptionClass, summary, detail)
    }

    /**
     * Sends even for a 4xx status. For client-facing responses whose frequency still needs to be
     * watched, such as the 409 a lock deadlock is mapped to; plain sendAlert drops every 4xx.
     */
    fun sendObservationAlert(
        request: HttpServletRequest,
        status: Int,
        exceptionClass: String,
        summary: String,
        detail: String? = null
    ) = deliver(request.location(), status, exceptionClass, summary, detail)

    /**
     * For failures that are swallowed on purpose so the user's request still succeeds (e.g. a
     * best-effort call to an outside service), where no exception reaches the global handler.
     * [source] names the operation and stands in for the request path.
     */
    fun sendOperationalAlert(
        source: String,
        exceptionClass: String,
        summary: String,
        detail: String? = null
    ) = deliver(source, OPERATIONAL_STATUS, exceptionClass, summary, detail)

    private fun HttpServletRequest.location(): String = "$method $requestURI"

    private fun deliver(
        location: String,
        status: Int,
        exceptionClass: String,
        summary: String,
        detail: String?
    ) {
        val client = restClient ?: return

        val dedupKey = "$status:$exceptionClass:$location"
        if (dedupCache.getIfPresent(dedupKey) != null) {
            log.debug("Skipping duplicate Discord alert: {}", dedupKey)
            return
        }
        dedupCache.put(dedupKey, true)

        val payload = buildPayload(location, status, exceptionClass, summary, detail)

        CompletableFuture.runAsync {
            try {
                client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity()
            } catch (ex: Exception) {
                log.warn("Failed to send Discord alert: {}", ex.message)
            }
        }
    }

    private fun buildPayload(
        location: String,
        status: Int,
        exceptionClass: String,
        summary: String,
        detail: String?
    ): Map<String, Any> {
        val color = 16711680
        val title = if (status >= SERVER_ERROR_MIN_STATUS) "서버 에러 ($status)" else "관측 알림 ($status)"

        val fields = mutableListOf(
            mapOf("name" to "Path", "value" to location, "inline" to false),
            mapOf("name" to "Exception", "value" to exceptionClass, "inline" to false),
            mapOf("name" to "Summary", "value" to summary.take(1024), "inline" to false)
        )

        if (!detail.isNullOrBlank()) {
            fields.add(mapOf("name" to "Detail", "value" to detail.take(1024), "inline" to false))
        }

        return mapOf(
            "embeds" to listOf(
                mapOf(
                    "title" to title,
                    "color" to color,
                    "fields" to fields,
                    "timestamp" to Instant.now().toString()
                )
            )
        )
    }

    private companion object {
        const val SERVER_ERROR_MIN_STATUS = 500

        // Shown as a server error: the user saw success, but an operation needs attention.
        const val OPERATIONAL_STATUS = 500
    }
}
