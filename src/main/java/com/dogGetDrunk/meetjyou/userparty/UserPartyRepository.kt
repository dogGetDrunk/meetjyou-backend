package com.dogGetDrunk.meetjyou.userparty

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserPartyRepository : JpaRepository<UserParty, Long> {
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
    fun deleteAllByParty_Uuid(partyUuid: UUID): Boolean
}
