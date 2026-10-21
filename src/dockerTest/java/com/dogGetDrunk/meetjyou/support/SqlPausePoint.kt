package com.dogGetDrunk.meetjyou.support

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.hibernate.resource.jdbc.spi.StatementInspector

/**
 * Lets a concurrency test stop a transaction right before a given SQL statement, so a race whose
 * window is a single statement (e.g. between two UPDATEs of one flush) can be reproduced
 * deterministically. Registered via `hibernate.session_factory.statement_inspector`; armed per
 * thread, so other transactions run through untouched.
 */
class SqlPausePoint : StatementInspector {

    override fun inspect(sql: String): String {
        val point = armed.get() ?: return sql
        if (sql.trimStart().startsWith(point.sqlPrefix, ignoreCase = true)) {
            armed.remove()
            point.reached.countDown()
            point.release.await(MAX_PAUSE_SECONDS, TimeUnit.SECONDS)
        }
        return sql
    }

    class Point(val sqlPrefix: String) {
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
    }

    companion object {
        private const val MAX_PAUSE_SECONDS = 30L
        private val armed = ThreadLocal<Point>()

        /** Arms a one-shot pause for the calling thread's next statement starting with [sqlPrefix]. */
        fun arm(point: Point) = armed.set(point)

        fun disarm() = armed.remove()
    }
}
