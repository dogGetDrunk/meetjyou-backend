package com.dogGetDrunk.meetjyou.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUuid(uuid: UUID): User?
    fun findByEmail(email: String): User?

    @Query(
        """
        SELECT a.user
        FROM UserSocialAccount a
        WHERE a.provider = :provider AND a.externalId = :externalId
        """
    )
    fun findBySocialAccount(@Param("provider") provider: AuthProvider, @Param("externalId") externalId: String): User?

    fun existsByUuid(uuid: UUID): Boolean
    fun existsByNickname(nickname: String): Boolean
    fun deleteByUuid(uuid: UUID): Int
    fun findAllByStatus(status: UserStatus): List<User>

    fun findAllByStatusAndWithdrawnAtLessThanEqual(status: UserStatus, cutoff: Instant): List<User>

    // Social accounts are owned by User; bulk removal for the purge job lives here instead of a separate repository.
    @Modifying
    @Query("DELETE FROM UserSocialAccount a WHERE a.user.id IN :userIds")
    fun deleteSocialAccountsByUserIdIn(@Param("userIds") userIds: Collection<Long>): Int

    @Query(
        """
        SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END
        FROM User u
        WHERE u.nickname = :nickname
          AND (u.status = com.dogGetDrunk.meetjyou.user.UserStatus.NORMAL OR u.withdrawnAt > :gracePeriodCutoff)
        """
    )
    fun existsActiveNickname(@Param("nickname") nickname: String, @Param("gracePeriodCutoff") gracePeriodCutoff: Instant): Boolean
}
