package com.dogGetDrunk.meetjyou.chat.support

import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Speaks STOMP over a plain WebSocket so a test can send frames a well-behaved client never would
 * (a second CONNECT, the STOMP alias, a SEND to a broker destination) and read every frame and the
 * close status the server answers with.
 */
class RawStompClient private constructor(
    private val session: WebSocketSession,
    private val frames: LinkedBlockingQueue<String>,
    val closeStatus: CompletableFuture<CloseStatus>,
) {

    fun connect(roomUuid: UUID, token: String?, command: String = CONNECT) {
        val auth = token?.let { "Authorization:Bearer $it\n" } ?: ""
        send("$command\naccept-version:1.2\nhost:localhost\nroomUuid:$roomUuid\n$auth\n$NULL")
    }

    fun subscribe(destination: String) {
        send("SUBSCRIBE\nid:sub-${UUID.randomUUID()}\ndestination:$destination\n\n$NULL")
    }

    fun sendTo(destination: String, body: String) {
        send("SEND\ndestination:$destination\ncontent-type:application/json\n\n$body$NULL")
    }

    /** First frame of [command] within [timeoutMillis], skipping others; null if none arrived. */
    fun awaitFrame(command: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): String? =
        awaitMatching(timeoutMillis) { it.startsWith("$command\n") }

    /** First frame whose text contains [marker] within [timeoutMillis]; null if none arrived. */
    fun awaitContaining(marker: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): String? =
        awaitMatching(timeoutMillis) { it.contains(marker) }

    fun isOpen(): Boolean = session.isOpen

    fun close() {
        if (session.isOpen) session.close()
    }

    private fun send(frame: String) = session.sendMessage(TextMessage(frame))

    private fun awaitMatching(timeoutMillis: Long, predicate: (String) -> Boolean): String? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        var match: String? = null
        while (match == null) {
            val remaining = deadline - System.nanoTime()
            val frame = if (remaining > 0) frames.poll(remaining, TimeUnit.NANOSECONDS) else null
            if (frame == null) break
            if (predicate(frame)) match = frame
        }
        return match
    }

    companion object {
        const val CONNECT = "CONNECT"
        const val STOMP = "STOMP"
        private const val NULL = "\u0000"
        private const val DEFAULT_TIMEOUT_MILLIS = 5000L
        private const val OPEN_TIMEOUT_SECONDS = 5L

        fun open(port: Int): RawStompClient {
            val frames = LinkedBlockingQueue<String>()
            val closeStatus = CompletableFuture<CloseStatus>()
            val handler = object : TextWebSocketHandler() {
                override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                    frames.add(message.payload)
                }

                override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
                    closeStatus.complete(status)
                }
            }
            val session = StandardWebSocketClient().execute(handler, "ws://localhost:$port/ws-chat")
                .get(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return RawStompClient(session, frames, closeStatus)
        }
    }
}
