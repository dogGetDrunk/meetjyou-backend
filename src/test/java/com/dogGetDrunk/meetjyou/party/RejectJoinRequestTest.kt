package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinRequestNotFoundException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

class RejectJoinRequestTest : BehaviorSpec() {

    private val partyRepository = mockk<PartyRepository>(relaxed = true)
    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val planRepository = mockk<PlanRepository>(relaxed = true)
    private val markerRepository = mockk<MarkerRepository>(relaxed = true)
    private val chatRoomRepository = mockk<ChatRoomRepository>(relaxed = true)
    private val chatParticipantService = mockk<ChatParticipantService>(relaxed = true)
    private val chatRoomEventBroadcaster = mockk<ChatRoomEventBroadcaster>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val partyImgService = mockk<PartyImgService>(relaxed = true)
    private val postImgService = mockk<PostImgService>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val sut = PartyService(
        partyRepository, postRepository, planRepository, markerRepository, chatRoomRepository,
        chatParticipantService, chatRoomEventBroadcaster, userPartyRepository,
        userRepository, publisher, partyImgService, postImgService, objectMapper, currentUserProvider,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("rejectJoinRequest 호출 시") {
            val host = UserFixtures.user()
            val applicant = UserFixtures.user(email = "app@test.com", nickname = "applicant", externalId = "ext2")
            val party = NotificationCenterFixtures.party()
            val hostMembership = NotificationCenterFixtures.hostUserParty(party, host)

            beforeEach {
                every { currentUserProvider.uuid } returns host.uuid
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership
            }

            `when`("PENDING 상태의 신청을 거절하면") {
                then("REJECTED로 전이되고 알림 이벤트가 발행된다") {
                    val pendingMembership = NotificationCenterFixtures.pendingUserParty(party, applicant)
                    every {
                        userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, applicant.uuid)
                    } returns pendingMembership

                    sut.rejectJoinRequest(party.uuid, applicant.uuid)

                    pendingMembership.memberStatus shouldBe MemberStatus.REJECTED
                    verify(exactly = 1) { publisher.publishEvent(any<Any>()) }
                }
            }

            `when`("이미 REJECTED 상태인 신청을 재거절 시도하면(응답 유실 재시도)") {
                then("예외 없이 조용히 종료하고 알림을 재발행하지 않는다") {
                    val rejectedMembership = NotificationCenterFixtures.pendingUserParty(party, applicant).also { it.reject() }
                    every {
                        userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, applicant.uuid)
                    } returns rejectedMembership

                    sut.rejectJoinRequest(party.uuid, applicant.uuid)

                    verify(exactly = 0) { publisher.publishEvent(any<Any>()) }
                }
            }

            `when`("BANNED 상태의 신청을 거절 시도하면") {
                then("PartyJoinRequestNotFoundException을 던진다") {
                    val bannedMembership = NotificationCenterFixtures.pendingUserParty(party, applicant).also { it.ban() }
                    every {
                        userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, applicant.uuid)
                    } returns bannedMembership

                    shouldThrow<PartyJoinRequestNotFoundException> {
                        sut.rejectJoinRequest(party.uuid, applicant.uuid)
                    }
                }
            }
        }
    }
}
