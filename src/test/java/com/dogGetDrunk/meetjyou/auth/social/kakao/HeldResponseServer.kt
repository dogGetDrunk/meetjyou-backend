package com.dogGetDrunk.meetjyou.auth.social.kakao

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Local HTTP server that accepts every request, marks it received and holds the response until
 * [release] (or [MAX_HOLD_SECONDS]). A client timeout against it is a read timeout on a live
 * connection, not a refused connection.
 */
class HeldResponseServer : AutoCloseable {
    val received = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val executor = Executors.newCachedThreadPool()
    private val server: HttpServer = HttpServer.create(InetSocketAddress(LOOPBACK, 0), 0)

    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            received.countDown()
            release.await(MAX_HOLD_SECONDS, TimeUnit.SECONDS)
            val body = "{}".toByteArray()
            exchange.sendResponseHeaders(OK, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    val baseUrl: String get() = "http://$LOOPBACK:${server.address.port}"

    fun release() = release.countDown()

    override fun close() {
        release()
        server.stop(0)
        executor.shutdown()
        executor.awaitTermination(MAX_HOLD_SECONDS, TimeUnit.SECONDS)
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val OK = 200
        const val MAX_HOLD_SECONDS = 5L
    }
}
