package com.dogGetDrunk.meetjyou.chat.support

import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRepository
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantRepository
import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.plan.Plan
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Component
class ChatTestDataHelper(
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository,
    private val partyRepository: PartyRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val userPartyRepository: UserPartyRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val notificationOutboxRepository: NotificationOutboxRepository,
) {
    data class TestData(val userUuid: UUID, val roomUuid: UUID)

    @Transactional
    fun createTestData(): TestData {
        val user = userRepository.save(
            User(
                email = "chat-test@example.com",
                nickname = "chatTester",
                authProvider = AuthProvider.KAKAO,
                externalId = "ext-chat-test",
            )
        )
        val plan = planRepository.save(
            Plan(
                title = "Seoul Trip",
                itinStart = Instant.now(),
                itinFinish = Instant.now().plusSeconds(86400),
                destination = "Seoul",
                centerLat = 37.5665,
                centerLng = 126.9780,
                owner = user,
            )
        )
        val party = partyRepository.save(
            Party(
                itinStart = Instant.now(),
                itinFinish = Instant.now().plusSeconds(86400),
                destination = "Seoul",
                joined = 1,
                capacity = 4,
                name = "Test Party",
            ).also { it.plan = plan }
        )
        val chatRoom = chatRoomRepository.save(ChatRoom(party = party))
        userPartyRepository.save(UserParty(party = party, user = user, role = PartyRole.MEMBER))
        return TestData(userUuid = user.uuid, roomUuid = chatRoom.uuid)
    }

    /** Adds a user to the room's party, then moves the membership to [status] through UserParty's own transitions. */
    @Transactional
    fun addMember(roomUuid: UUID, nickname: String, status: MemberStatus): UUID {
        val party = chatRoomRepository.findByUuid(roomUuid)?.party ?: error("Room not found: $roomUuid")
        val user = userRepository.save(
            User(
                email = "$nickname@example.com",
                nickname = nickname,
                authProvider = AuthProvider.KAKAO,
                externalId = "ext-$nickname",
            )
        )
        val membership = UserParty(party = party, user = user, role = PartyRole.MEMBER)
        applyStatus(membership, status)
        userPartyRepository.save(membership)
        return user.uuid
    }

    private fun applyStatus(membership: UserParty, status: MemberStatus) {
        when (status) {
            MemberStatus.JOINED -> Unit
            MemberStatus.PENDING -> membership.pending()
            MemberStatus.REJECTED -> membership.reject()
            MemberStatus.BANNED -> membership.ban()
            MemberStatus.LEFT -> membership.leave()
        }
    }

    @Transactional(readOnly = true)
    fun outboxRecipientUuids(): Set<UUID> =
        notificationOutboxRepository.findAll().map { it.user.uuid }.toSet()

    @Transactional
    fun cleanup() {
        userPartyRepository.deleteAll()
        chatMessageRepository.deleteAll()
        chatParticipantRepository.deleteAll()
        chatRoomRepository.deleteAll()
        partyRepository.deleteAll()
        planRepository.deleteAll()
        notificationOutboxRepository.deleteAll()
        userRepository.deleteAll()
    }
}
