package com.dogGetDrunk.meetjyou.userparty

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserPartyRepository : JpaRepository<UserParty, Long> {
    fun findAllByUser_Uuid(userUuid: UUID, pageable: Pageable): Page<UserParty>
    fun findAllByParty_Uuid(partyUuid: UUID, pageable: Pageable): Page<UserParty>
    fun findAllByParty_Uuid(partyUuid: UUID): List<UserParty>
}
