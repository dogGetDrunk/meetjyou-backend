package com.dogGetDrunk.meetjyou.party.dto

import com.dogGetDrunk.meetjyou.user.dto.UserDisplay
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import java.time.Instant
import java.util.UUID

data class PartyMemberResponse(
    val userUuid: UUID,
    val nickname: String,
    val hasProfileImage: Boolean,
    val isWithdrawn: Boolean,
    val role: PartyRole,
    val joinedAt: Instant,
) {
    companion object {
        fun of(userParty: UserParty): PartyMemberResponse {
            val display = UserDisplay.of(userParty.user)
            return PartyMemberResponse(
                userUuid = userParty.user.uuid,
                nickname = display.nickname,
                hasProfileImage = display.hasProfileImage,
                isWithdrawn = display.isWithdrawn,
                role = userParty.role,
                joinedAt = userParty.joinedAt,
            )
        }
    }
}
