package com.dogGetDrunk.meetjyou.common.discord

import com.dogGetDrunk.meetjyou.config.DiscordProperties
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.concurrent.CompletableFuture

@Service
class DiscordAlertService(props: DiscordProperties) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = if (props.webhookUrl.isNotBlank()) RestClient.create(props.webhookUrl) else null

    fun sendAlert(
        request: HttpServletRequest,
        status: Int,
        exceptionClass: String,
        summary: String,
        detail: String? = null
    ) {
        val client = restClient ?: return

        val isServerError = status >= 500
        val color = if (isServerError) 16711680 else 16763904
        val title = if (isServerError) "서버 에러 ($status)" else "클라이언트 에러 ($status)"

        val fields = mutableListOf(
            mapOf("name" to "Path", "value" to "${request.method} ${request.requestURI}", "inline" to false),
            mapOf("name" to "Exception", "value" to exceptionClass, "inline" to false),
            mapOf("name" to "Summary", "value" to summary.take(1024), "inline" to false)
        )

        if (!detail.isNullOrBlank()) {
            fields.add(mapOf("name" to "Detail", "value" to detail.take(1024), "inline" to false))
        }

        val payload = mapOf(
            "embeds" to listOf(
                mapOf(
                    "title" to title,
                    "color" to color,
                    "fields" to fields,
                    "timestamp" to Instant.now().toString()
                )
            )
        )

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
}
