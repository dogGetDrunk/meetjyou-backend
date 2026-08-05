package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinAlreadyMemberException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
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

class RequestJoinPartyTest : BehaviorSpec() {

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

        given("requestJoinParty 호출 시") {
            val applicant = UserFixtures.user()
            val party = NotificationCenterFixtures.party()
            val partyUuid = party.uuid
            val applicantUuid = applicant.uuid

            beforeEach {
                every { currentUserProvider.uuid } returns applicantUuid
                every { partyRepository.findByUuid(partyUuid) } returns party
                every { userRepository.findByUuid(applicantUuid) } returns applicant
            }

            `when`("신청 이력이 없으면") {
                then("PENDING 상태로 신규 신청이 저장된다") {
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, applicantUuid) } returns null
                    every { userPartyRepository.save(any()) } answers { firstArg() }

                    val response = sut.requestJoinParty(partyUuid, "hi")

                    response.status shouldBe "PENDING"
                    verify(exactly = 1) { userPartyRepository.save(any()) }
                }
            }

            `when`("이미 PENDING 상태인 신청을 다시 제출하면(응답 유실 재시도)") {
                then("예외 없이 동일한 PENDING 응답을 반환하고 새 레코드를 만들지 않는다") {
                    val pendingMembership = NotificationCenterFixtures.pendingUserParty(party, applicant)
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, applicantUuid) } returns pendingMembership

                    val response = sut.requestJoinParty(partyUuid, "hi")

                    response.status shouldBe "PENDING"
                    verify(exactly = 0) { userPartyRepository.save(any()) }
                }
            }

            `when`("이미 JOINED 상태인 유저가 신청하면") {
                then("PartyJoinAlreadyMemberException을 던진다") {
                    val joinedMembership = NotificationCenterFixtures.pendingUserParty(party, applicant).also { it.approve() }
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, applicantUuid) } returns joinedMembership

                    shouldThrow<PartyJoinAlreadyMemberException> {
                        sut.requestJoinParty(partyUuid, "hi")
                    }
                }
            }
        }
    }
}
