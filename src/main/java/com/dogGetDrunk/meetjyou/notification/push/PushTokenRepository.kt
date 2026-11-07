package com.dogGetDrunk.meetjyou.notification.push

import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PushTokenRepository : JpaRepository<PushToken, Long> {
    fun findAllByUserIdInAndActiveTrue(userIds: Collection<Long>): List<PushToken>
    fun findByToken(token: String): PushToken?

    @Transactional
    @Modifying
    @Query("update PushToken pt set pt.active = false where pt.id in :ids")
    fun deactivateAllByIdIn(@Param("ids") ids: Collection<Long>)

    @Modifying
    @Query("delete from PushToken pt where pt.user.id in :userIds")
    fun deleteAllByUserIdIn(@Param("userIds") userIds: Collection<Long>): Int
}
