package com.dogGetDrunk.meetjyou.preference

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserPreferenceRepository : JpaRepository<UserPreference, Long> {
    @Modifying
    @Query("DELETE FROM UserPreference up WHERE up.user.id = :userId AND up.preference.type = :type")
    fun deleteByUserIdAndType(@Param("userId") userId: Long, @Param("type") type: PreferenceType)

    fun findAllByUser_IdIn(userIds: List<Long>): List<UserPreference>

    @Modifying
    @Query("DELETE FROM UserPreference up WHERE up.user.id IN :userIds")
    fun deleteAllByUserIdIn(@Param("userIds") userIds: Collection<Long>): Int
}
