package com.dogGetDrunk.meetjyou.chat

import org.springframework.context.annotation.Profile
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.nio.file.Path

@Profile("dev")
@RestController
class DevChatTestController {

    @GetMapping("/chat-test-client.html", produces = [MediaType.TEXT_HTML_VALUE])
    fun getChatTestClient(): ResponseEntity<String> {
        val htmlPath = Path.of("docs", "chat", "test.html")

        if (!Files.exists(htmlPath)) {
            return ResponseEntity.notFound().build()
        }

        val html = Files.readString(htmlPath)
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.TEXT_HTML)
            .body(html)
    }
}
