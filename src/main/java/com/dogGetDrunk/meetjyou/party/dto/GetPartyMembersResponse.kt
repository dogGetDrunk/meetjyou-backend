package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import java.time.Instant
import java.util.UUID

data class PartyMemberResponse(
    val userUuid: UUID,
    val nickname: String,
    val hasProfileImage: Boolean,
    val role: PartyRole,
    val joinedAt: Instant,
) {
    companion object {
        fun of(userParty: UserParty): PartyMemberResponse {
            return PartyMemberResponse(
                userUuid = userParty.user.uuid,
                nickname = userParty.user.nickname,
                hasProfileImage = userParty.user.hasProfileImage,
                role = userParty.role,
                joinedAt = userParty.joinedAt,
            )
        }
    }
}
