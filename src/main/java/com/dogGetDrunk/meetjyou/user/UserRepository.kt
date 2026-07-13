package com.dogGetDrunk.meetjyou.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUuid(uuid: UUID): User?
    fun findByEmail(email: String): User?
    fun findByAuthProviderAndExternalId(authProvider: AuthProvider, externalId: String): User?
    fun existsByUuid(uuid: UUID): Boolean
    fun existsByEmail(email: String): Boolean
    fun existsByNickname(nickname: String): Boolean
    fun existsByAuthProviderAndExternalId(authProvider: AuthProvider, externalId: String): Boolean
    fun deleteByUuid(uuid: UUID): Int
    fun findAllByStatus(status: UserStatus): List<User>

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
