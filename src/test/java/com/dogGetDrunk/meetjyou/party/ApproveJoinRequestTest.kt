package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyFullException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinRequestNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

class ApproveJoinRequestTest : BehaviorSpec() {

    private val partyRepository = mockk<PartyRepository>(relaxed = true)
    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val planRepository = mockk<PlanRepository>(relaxed = true)
    private val chatRoomRepository = mockk<ChatRoomRepository>(relaxed = true)
    private val chatParticipantService = mockk<ChatParticipantService>(relaxed = true)
    private val chatRoomEventBroadcaster = mockk<ChatRoomEventBroadcaster>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val sut = PartyService(
        partyRepository, postRepository, planRepository, chatRoomRepository,
        chatParticipantService, chatRoomEventBroadcaster, userPartyRepository,
        userRepository, publisher,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("approveJoinRequest 호출 시") {
            val host = UserFixtures.user()
            val applicant = UserFixtures.user(email = "app@test.com", nickname = "applicant", externalId = "ext2")
            val party = NotificationCenterFixtures.party()
            val hostMembership = NotificationCenterFixtures.hostUserParty(party, host)

            beforeEach {
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership
                every { partyRepository.findByUuidForUpdate(party.uuid) } returns party
                every { userRepository.findByUuid(applicant.uuid) } returns applicant
            }

            `when`("정상적으로 승인하면") {
                then("joined 카운터가 1 증가하고 알림 이벤트가 발행된다") {
                    val pendingMembership = NotificationCenterFixtures.pendingUserParty(party, applicant)
                    every {
                        userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, applicant.uuid)
                    } returns pendingMembership
                    every {
                        userPartyRepository.findAllWithUserByPartyUuidAndMemberStatus(party.uuid, MemberStatus.JOINED)
                    } returns listOf(hostMembership)

                    val joinedBefore = party.joined
                    sut.approveJoinRequest(party.uuid, host.uuid, applicant.uuid)

                    party.joined shouldBe joinedBefore + 1
                    pendingMembership.memberStatus shouldBe MemberStatus.JOINED
                    verify(exactly = 2) { publisher.publishEvent(any<Any>()) } // applicant: JOIN_ACCEPTED, host: MEMBER_JOINED
                }
            }

            `when`("정원이 꽉 찬 파티에서 승인 시도하면") {
                then("PartyFullException을 던진다") {
                    val fullParty = Party(
                        itinStart = party.itinStart,
                        itinFinish = party.itinFinish,
                        destination = party.destination,
                        joined = 5,
                        capacity = 5,
                        name = party.name,
                    )
                    every { partyRepository.findByUuidForUpdate(party.uuid) } returns fullParty

                    shouldThrow<PartyFullException> {
                        sut.approveJoinRequest(party.uuid, host.uuid, applicant.uuid)
                    }
                }
            }

            `when`("PENDING이 아닌 상태의 신청을 승인 시도하면") {
                then("PartyJoinRequestNotFoundException을 던진다") {
                    val joinedMembership = NotificationCenterFixtures.hostUserParty(party, applicant)
                    every {
                        userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, applicant.uuid)
                    } returns joinedMembership

                    shouldThrow<PartyJoinRequestNotFoundException> {
                        sut.approveJoinRequest(party.uuid, host.uuid, applicant.uuid)
                    }
                }
            }

            `when`("호스트가 아닌 유저가 승인 시도하면") {
                then("PartyUpdateAccessDeniedException을 던진다") {
                    val nonHost = UserFixtures.user(email = "other@test.com", nickname = "other", externalId = "ext3")
                    every {
                        userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, nonHost.uuid)
                    } returns NotificationCenterFixtures.pendingUserParty(party, nonHost)

                    shouldThrow<PartyUpdateAccessDeniedException> {
                        sut.approveJoinRequest(party.uuid, nonHost.uuid, applicant.uuid)
                    }
                }
            }
        }
    }
}
