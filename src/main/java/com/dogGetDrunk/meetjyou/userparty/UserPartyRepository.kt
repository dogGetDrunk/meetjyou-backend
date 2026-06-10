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

    fun findAllByUser_Uuid(
        userUuid: UUID,
        pageable: Pageable
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

    @Query("""
        select up from UserParty up
        join fetch up.party
        where up.user.uuid = :userUuid
        and up.role = 'MEMBER'
        and up.memberStatus in ('PENDING', 'JOINED', 'REJECTED')
        order by up.statusChangedAt desc
    """)
    fun findAllSentApplicationsByUserUuid(@Param("userUuid") userUuid: UUID): List<UserParty>

}
