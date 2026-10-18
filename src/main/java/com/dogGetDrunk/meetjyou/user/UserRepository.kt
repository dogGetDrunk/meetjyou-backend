package com.dogGetDrunk.meetjyou.user

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
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
    fun findAllByStatus(status: UserStatus): List<User>

    fun findAllByStatusAndWithdrawnAtLessThanEqual(status: UserStatus, cutoff: Instant): List<User>

    // Locking read: always sees the latest committed rows, whatever this transaction read before.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM UserSocialAccount a WHERE a.user.id = :userId")
    fun findSocialAccountsForUpdate(@Param("userId") userId: Long): List<UserSocialAccount>

    // Social accounts are owned by User; bulk removal for the purge job lives here instead of a separate repository.
    @Modifying
    @Query("DELETE FROM UserSocialAccount a WHERE a.user.id IN :userIds")
    fun deleteSocialAccountsByUserIdIn(@Param("userIds") userIds: Collection<Long>): Int
}
