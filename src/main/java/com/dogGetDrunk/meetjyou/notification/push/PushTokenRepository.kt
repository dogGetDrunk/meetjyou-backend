package com.dogGetDrunk.meetjyou.notification.push

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PushTokenRepository : JpaRepository<PushToken, Long> {
    fun findAllByUserIdAndIsActiveTrue(userId: Long): List<PushToken>
    fun findByToken(token: String): PushToken?

    @Query("select count(pt) > 0 from PushToken pt where pt.user.id = :userId and pt.token = :token")
    fun existsByUserIdAndToken(userId: Long, token: String): Boolean
}
