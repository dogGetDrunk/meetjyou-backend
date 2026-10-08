package com.dogGetDrunk.meetjyou.chat.connection

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.UserStatus
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import java.util.UUID

class ChatStompInterceptorConnectTest : BehaviorSpec() {

    private val jwtProvider = mockk<JwtProvider>()
    private val chatRoomRepository = mockk<ChatRoomRepository>()
    private val userPartyRepository = mockk<UserPartyRepository>()
    private val userRepository = mockk<UserRepository>()
    private val channel = mockk<MessageChannel>()
    private val sut = ChatStompInterceptor(jwtProvider, chatRoomRepository, userPartyRepository, userRepository)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val roomUuid = UUID.randomUUID()
    private val partyUuid = UUID.randomUUID()

    private fun connectMessage(token: String) =
        StompHeaderAccessor.create(StompCommand.CONNECT).run {
            addNativeHeader("roomUuid", roomUuid.toString())
            addNativeHeader("Authorization", "Bearer $token")
            setSessionAttributes(mutableMapOf())
            setLeaveMutable(true)
            MessageBuilder.createMessage(ByteArray(0), messageHeaders)
        }

    private fun stubActiveMembership(userUuid: UUID) {
        val membership = mockk<UserParty>()
        every { membership.isActiveMember() } returns true
        every { chatRoomRepository.findPartyUuidByRoomUuid(roomUuid) } returns partyUuid
        every { userPartyRepository.findByParty_UuidAndUser_Uuid(partyUuid, userUuid) } returns membership
    }

    init {
        given("STOMP CONNECT 요청에서") {
            val user = UserFixtures.user()

            `when`("유효한 access token이고 활성 유저·멤버이면") {
                then("연결을 허용한다") {
                    every { jwtProvider.validateAccessTokenOrThrow("access") } returns Unit
                    every { jwtProvider.getUserUuid("access") } returns user.uuid
                    every { userRepository.findByUuid(user.uuid) } returns user
                    stubActiveMembership(user.uuid)

                    shouldNotThrowAny { sut.preSend(connectMessage("access"), channel) }
                }
            }

            `when`("refresh token을 보내면") {
                then("연결을 거부한다") {
                    // Everything except the token-type check would let this connection through, so
                    // the rejection can only come from that check.
                    every { jwtProvider.validateAccessTokenOrThrow("refresh") } throws InvalidJwtException()
                    every { jwtProvider.getUserUuid("refresh") } returns user.uuid
                    every { userRepository.findByUuid(user.uuid) } returns user
                    stubActiveMembership(user.uuid)

                    shouldThrow<IllegalArgumentException> { sut.preSend(connectMessage("refresh"), channel) }
                }
            }

            `when`("탈퇴한 유저의 access token이면") {
                then("연결을 거부한다") {
                    user.status = UserStatus.DELETED
                    every { jwtProvider.validateAccessTokenOrThrow("access") } returns Unit
                    every { jwtProvider.getUserUuid("access") } returns user.uuid
                    every { userRepository.findByUuid(user.uuid) } returns user
                    stubActiveMembership(user.uuid)

                    shouldThrow<IllegalArgumentException> { sut.preSend(connectMessage("access"), channel) }
                }
            }
        }
    }
}
