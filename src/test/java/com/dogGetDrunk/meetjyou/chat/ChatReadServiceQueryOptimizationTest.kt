package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRepository
import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

// Regression tests for the chat-module query-duplication fixes:
// - getChatRooms must fetch the caller's memberships once and reuse them for unread counting,
//   not re-query the identical membership list inside buildUnreadCountMap.
// - markLatestMessageAsReadForUsers must resolve the room's partyUuid once and batch-load all
//   target users' memberships in one query, not once per user in the loop.
class ChatReadServiceQueryOptimizationTest : BehaviorSpec() {

    private val chatMessageRepository = mockk<ChatMessageRepository>(relaxed = true)
    private val chatRoomRepository = mockk<ChatRoomRepository>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val chatRoomEventBroadcaster = mockk<ChatRoomEventBroadcaster>(relaxed = true)
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)

    private val sut = ChatReadService(
        chatMessageRepository, chatRoomRepository, userPartyRepository, chatRoomEventBroadcaster, currentUserProvider,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        given("getChatRooms 호출 시") {
            `when`("본인이 가입한 파티가 있으면") {
                then("본인 멤버십을 한 번만 조회하고 unread count 계산에 그대로 재사용한다") {
                    val requester = UserFixtures.user()
                    val party = NotificationCenterFixtures.party()
                    val membership = NotificationCenterFixtures.hostUserParty(party, requester)
                    val room = ChatRoom(party = party)

                    every { currentUserProvider.uuid } returns requester.uuid
                    every {
                        userPartyRepository.findAllWithPartyByUserUuidAndMemberStatus(requester.uuid, MemberStatus.JOINED)
                    } returns listOf(membership)
                    every { chatRoomRepository.findAllWithPartyByPartyUuidIn(setOf(party.uuid)) } returns listOf(room)

                    sut.getChatRooms()

                    verify(exactly = 1) {
                        userPartyRepository.findAllWithPartyByUserUuidAndMemberStatus(requester.uuid, MemberStatus.JOINED)
                    }
                }
            }
        }

        given("markLatestMessageAsReadForUsers 호출 시") {
            `when`("여러 유저의 읽음 위치를 갱신하면") {
                then("유저별 개별 조회 대신 파티 조회 1회 + 멤버십 배치 조회 1회만 수행한다") {
                    val party = NotificationCenterFixtures.party()
                    val roomUuid = UUID.randomUUID()
                    val userA = UserFixtures.user(email = "a@example.com", nickname = "a")
                    val userB = UserFixtures.user(email = "b@example.com", nickname = "b")
                    val membershipA = UserParty(party, userA, PartyRole.MEMBER)
                    val membershipB = UserParty(party, userB, PartyRole.MEMBER)
                    val targetUuids = setOf(userA.uuid, userB.uuid)

                    every { chatRoomRepository.findPartyUuidByRoomUuid(roomUuid) } returns party.uuid
                    every {
                        userPartyRepository.findAllByParty_UuidAndUser_UuidIn(party.uuid, targetUuids)
                    } returns listOf(membershipA, membershipB)

                    sut.markLatestMessageAsReadForUsers(roomUuid, targetUuids, 42L)

                    verify(exactly = 1) { chatRoomRepository.findPartyUuidByRoomUuid(roomUuid) }
                    verify(exactly = 1) { userPartyRepository.findAllByParty_UuidAndUser_UuidIn(party.uuid, targetUuids) }
                    verify(exactly = 0) { userPartyRepository.findByParty_UuidAndUser_Uuid(any(), any()) }
                }
            }
        }
    }
}
