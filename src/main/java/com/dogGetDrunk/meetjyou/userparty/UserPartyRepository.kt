package com.dogGetDrunk.meetjyou.userparty

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserPartyRepository : JpaRepository<UserParty, Long> {

    fun findByParty_UuidAndRole(partyUuid: UUID, role: PartyRole): UserParty?

    @Query("""
        select up from UserParty up
        join fetch up.user
        where up.party.uuid = :partyUuid and up.memberStatus = :memberStatus
    """)
    fun findAllWithUserByPartyUuidAndMemberStatus(
        @Param("partyUuid") partyUuid: UUID,
        @Param("memberStatus") memberStatus: MemberStatus,
    ): List<UserParty>
    fun findByParty_UuidAndUser_Uuid(
        partyUuid: UUID,
        userUuid: UUID
    ): UserParty?

    fun findAllByParty_UuidInAndUser_Uuid(
        partyUuids: List<UUID>,
        userUuid: UUID,
    ): List<UserParty>

    fun findAllByParty_UuidAndUser_UuidIn(
        partyUuid: UUID,
        userUuids: Collection<UUID>,
    ): List<UserParty>

    fun findAllByUser_Uuid(
        userUuid: UUID,
        pageable: Pageable
    ): Page<UserParty>

    @Query(
        value = """
            select up
            from UserParty up
            join fetch up.party p
            left join fetch p.plan
            where up.user.uuid = :userUuid
        """,
        countQuery = """
            select count(up)
            from UserParty up
            where up.user.uuid = :userUuid
        """
    )
    fun findAllWithPartyByUserUuid(
        @Param("userUuid") userUuid: UUID,
        pageable: Pageable,
    ): Page<UserParty>

    fun findAllByParty_Uuid(partyUuid: UUID): List<UserParty>

    fun existsByParty_UuidAndUser_Uuid(
        partyUuid: UUID,
        userUuid: UUID
    ): Boolean

    fun existsByParty_UuidAndUser_UuidAndMemberStatus(
        partyUuid: UUID,
        userUuid: UUID,
        memberStatus: MemberStatus
    ): Boolean

    @Query(
        """
        select up
        from UserParty up
        join fetch up.user
        where up.party.uuid = :partyUuid
        """
    )
    fun findAllWithUserByPartyUuid(@Param("partyUuid") partyUuid: UUID): List<UserParty>

    @Query(
        """
        select up
        from UserParty up
        join fetch up.party
        where up.user.uuid = :userUuid
          and up.memberStatus = :memberStatus
        """
    )
    fun findAllWithPartyByUserUuidAndMemberStatus(
        @Param("userUuid") userUuid: UUID,
        @Param("memberStatus") memberStatus: MemberStatus,
    ): List<UserParty>

    @Query(
        value = """
            select up
            from UserParty up
            join fetch up.party
            where up.user.uuid = :userUuid
              and up.memberStatus = :memberStatus
        """,
        countQuery = """
            select count(up)
            from UserParty up
            where up.user.uuid = :userUuid
              and up.memberStatus = :memberStatus
        """
    )
    fun findAllWithPartyByUserUuidAndMemberStatus(
        @Param("userUuid") userUuid: UUID,
        @Param("memberStatus") memberStatus: MemberStatus,
        pageable: Pageable,
    ): Page<UserParty>

    fun existsByParty_Plan_UuidAndUser_UuidAndMemberStatus(
        planUuid: UUID,
        userUuid: UUID,
        memberStatus: MemberStatus,
    ): Boolean

    fun deleteAllByParty_Uuid(partyUuid: UUID): Int

    fun deleteByParty_UuidAndUser_Uuid(partyUuid: UUID, userUuid: UUID): Int

    @Query("""
        select up from UserParty up
        join fetch up.user
        join fetch up.party
        where up.party in (
            select h.party from UserParty h
            where h.user.uuid = :hostUuid and h.role = 'HOST' and h.memberStatus = 'JOINED'
        )
        and up.memberStatus = 'PENDING'
        order by up.joinedAt desc
    """)
    fun findAllPendingRequestsForHost(@Param("hostUuid") hostUuid: UUID): List<UserParty>

    @Query(
        value = """
            select up from UserParty up
            join fetch up.user
            join fetch up.party
            where up.party in (
                select h.party from UserParty h
                where h.user.uuid = :hostUuid and h.role = 'HOST' and h.memberStatus = 'JOINED'
            )
            and up.memberStatus = 'PENDING'
            order by up.joinedAt desc
        """,
        countQuery = """
            select count(up) from UserParty up
            where up.party in (
                select h.party from UserParty h
                where h.user.uuid = :hostUuid and h.role = 'HOST' and h.memberStatus = 'JOINED'
            )
            and up.memberStatus = 'PENDING'
        """
    )
    fun findAllPendingRequestsForHost(@Param("hostUuid") hostUuid: UUID, pageable: Pageable): Page<UserParty>

    @Query("""
        select count(up) from UserParty up
        where up.party in (
            select h.party from UserParty h
            where h.user.uuid = :hostUuid and h.role = 'HOST' and h.memberStatus = 'JOINED'
        )
        and up.memberStatus = 'PENDING'
        and up.hostRead = false
    """)
    fun countUnreadPendingRequestsForHost(@Param("hostUuid") hostUuid: UUID): Long

    @Query("""
        select up from UserParty up
        join fetch up.party
        where up.user.uuid = :userUuid
        and up.role = 'MEMBER'
        and up.memberStatus in ('PENDING', 'JOINED', 'REJECTED')
        order by up.statusChangedAt desc
    """)
    fun findAllSentApplicationsByUserUuid(@Param("userUuid") userUuid: UUID): List<UserParty>

    @Query("""
        select count(up) from UserParty up
        where up.user.uuid = :userUuid
        and up.role = 'MEMBER'
        and up.memberStatus = 'PENDING'
    """)
    fun countPendingSentApplications(@Param("userUuid") userUuid: UUID): Long

    @Query("""
        select count(up) from UserParty up
        where up.user.uuid = :userUuid
        and up.role = 'MEMBER'
        and up.memberStatus in ('JOINED', 'REJECTED')
        and up.applicantRead = false
    """)
    fun countChangedUnreadSentApplications(@Param("userUuid") userUuid: UUID): Long

    @Query(
        value = """
            select up from UserParty up
            join fetch up.party
            where up.user.uuid = :userUuid
            and up.role = 'MEMBER'
            and up.memberStatus in ('PENDING', 'JOINED', 'REJECTED')
        """,
        countQuery = """
            select count(up) from UserParty up
            where up.user.uuid = :userUuid
            and up.role = 'MEMBER'
            and up.memberStatus in ('PENDING', 'JOINED', 'REJECTED')
        """
    )
    fun findAllSentApplicationsByUserUuid(
        @Param("userUuid") userUuid: UUID,
        pageable: Pageable,
    ): Page<UserParty>

}
