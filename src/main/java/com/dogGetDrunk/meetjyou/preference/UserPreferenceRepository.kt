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
    fun deleteByUserIdAndType(@Param("userId") userId: Long, @Param("type") type: Int)

    @Query("SELECT up.preference FROM UserPreference up WHERE up.user.id = :userId AND up.preference.type = :type")
    fun findPreferenceByUserIdAndType(@Param("userId") userId: Long, @Param("type") type: Int): Preference?

    @Query("SELECT up.preference FROM UserPreference up WHERE up.user.id = :userId AND up.preference.type = :type")
    fun findPreferencesByUserIdAndType(@Param("userId") userId: Long, @Param("type") type: Int): List<Preference>
}
