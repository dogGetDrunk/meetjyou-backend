package com.dogGetDrunk.meetjyou.notification.push

import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PushTokenRepository : JpaRepository<PushToken, Long> {
    fun findAllByUserIdAndActiveTrue(userId: Long): List<PushToken>
    fun findAllByUserIdInAndActiveTrue(userIds: Collection<Long>): List<PushToken>
    fun findByToken(token: String): PushToken?

    @Query("select count(pt) > 0 from PushToken pt where pt.user.id = :userId and pt.token = :token")
    fun existsByUserIdAndToken(userId: Long, token: String): Boolean

    @Transactional
    @Modifying
    @Query("update PushToken pt set pt.active = false where pt.id in :ids")
    fun deactivateAllByIdIn(@Param("ids") ids: Collection<Long>)
}
