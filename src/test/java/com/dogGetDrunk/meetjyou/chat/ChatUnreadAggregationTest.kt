package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRepository
import com.dogGetDrunk.meetjyou.chat.message.RoomUnreadCountProjection
import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class ChatUnreadAggregationTest : StringSpec({
    // AT-001-1: the actual room-list path aggregates once regardless of room count.
    "AT-001-1 aggregates the complete room list once and maps missing projections to zero" {
        val messages = mockk<ChatMessageRepository>(relaxed = true)
        val roomsRepository = mockk<ChatRoomRepository>(relaxed = true)
        val membershipsRepository = mockk<UserPartyRepository>(relaxed = true)
        val currentUser = mockk<CurrentUserProvider>()
        val service = ChatReadService(messages, roomsRepository, membershipsRepository, mockk<ChatRoomEventBroadcaster>(), currentUser)
        val requester = UUID.randomUUID()
        every { currentUser.uuid } returns requester

        listOf(0, 1, 10).forEach { roomCount ->
            clearMocks(messages, roomsRepository, membershipsRepository)
            val parties = List(roomCount) {
                val party = mockk<Party>()
                every { party.uuid } returns UUID.randomUUID()
                every { party.name } returns "party-$it"
                party
            }
            val memberships = parties.mapIndexed { index, party ->
                val membership = mockk<UserParty>()
                every { membership.party } returns party
                every { membership.lastReadMessageId } returns if (index % 2 == 0) null else index.toLong()
                membership
            }
            val rooms = parties.map { ChatRoom(party = it) }
            val expected = rooms.mapIndexedNotNull { index, room ->
                if (roomCount > 1 && index == roomCount - 1) null else room.uuid to (index + 2L)
            }.toMap()
            val projections = expected.map { (uuid, count) ->
                val projection = mockk<RoomUnreadCountProjection>()
                every { projection.getRoomUuid() } returns uuid
                every { projection.getUnreadCount() } returns count
                projection
            }.reversed()
            every {
                membershipsRepository.findAllWithPartyByUserUuidAndMemberStatus(requester, MemberStatus.JOINED)
            } returns memberships
            every { roomsRepository.findAllWithPartyByPartyUuidIn(any()) } returns rooms
            every { messages.findLatestMessagesByRoomUuids(any()) } returns emptyList()
            every { messages.countUnreadByRoomUuids(any(), any()) } returns projections

            val result = service.getChatRooms()

            result.rooms.associate { it.roomUuid to it.unreadCount } shouldBe
                rooms.associate { it.uuid to (expected[it.uuid] ?: 0L) }
            if (roomCount == 0) {
                verify(exactly = 0) { messages.countUnreadByRoomUuids(any(), any()) }
            } else {
                var capturedRooms: Set<UUID> = emptySet()
                var capturedRequester: UUID? = null
                verify(exactly = 1) {
                    messages.countUnreadByRoomUuids(
                        match { capturedRooms = it.toSet(); true },
                        match { capturedRequester = it; true },
                    )
                }
                capturedRooms shouldBe rooms.map { it.uuid }.toSet()
                capturedRequester shouldBe requester
                verify(exactly = 1) { messages.findLatestMessagesByRoomUuids(any()) }
            }
            confirmVerified(messages)
        }
    }
})
