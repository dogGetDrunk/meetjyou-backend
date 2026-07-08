package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.common.exception.business.plan.PlanReadAccessDeniedException
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PlanAccessGuard(
    private val postRepository: PostRepository,
    private val userPartyRepository: UserPartyRepository,
) {
    fun validateReadAccess(plan: Plan, currentUserUuid: UUID) {
        val canRead = plan.owner.uuid == currentUserUuid
            || postRepository.existsByPlan_UuidAndIsPlanPublicTrue(plan.uuid)
            || userPartyRepository.existsByParty_Plan_UuidAndUser_UuidAndMemberStatus(
                plan.uuid, currentUserUuid, MemberStatus.JOINED,
            )
        if (!canRead) {
            throw PlanReadAccessDeniedException(plan.uuid, currentUserUuid)
        }
    }
}
