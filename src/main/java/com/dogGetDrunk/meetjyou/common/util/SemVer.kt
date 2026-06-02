package com.dogGetDrunk.meetjyou.common.util

object SemVer {
    fun compare(a: String, b: String): Int {
        val aParts = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bParts = b.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(aParts.size, bParts.size)
        for (i in 0 until len) {
            val diff = aParts.getOrElse(i) { 0 } - bParts.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }
}
