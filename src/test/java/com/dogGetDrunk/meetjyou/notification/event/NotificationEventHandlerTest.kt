package com.dogGetDrunk.meetjyou.notification.event

import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.notification.preference.NotificationPreferenceService
import com.dogGetDrunk.meetjyou.notification.template.NotificationTemplate
import com.dogGetDrunk.meetjyou.notification.template.NotificationTemplateFactory
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class NotificationEventHandlerTest : BehaviorSpec() {
    private val templateFactory = mockk<NotificationTemplateFactory>()
    private val outboxRepository = mockk<NotificationOutboxRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val preferenceService = mockk<NotificationPreferenceService>()
    private val objectMapper = ObjectMapper()
    private val sut = NotificationEventHandler(templateFactory, outboxRepository, userRepository, preferenceService, objectMapper)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun event(userUuid: UUID = UUID.randomUUID()) = NotificationEvent(
        userUuid = userUuid,
        payload = NotificationPayload(type = NotificationType.CHAT_MESSAGE),
    )

    init {
        beforeEach { clearAllMocks() }

        given("on(NotificationEvent) 호출 시") {

            `when`("UUID에 해당하는 유저가 없으면") {
                then("예외 없이 종료되고 outbox가 저장되지 않는다") {
                    val event = event()
                    every { userRepository.findByUuid(event.userUuid) } returns null

                    sut.on(event)

                    verify(exactly = 0) { outboxRepository.save(any()) }
                }
            }

            `when`("알림 설정이 꺼져 있으면") {
                then("outbox가 저장되지 않는다") {
                    val user = UserFixtures.user()
                    val event = event(user.uuid)
                    every { userRepository.findByUuid(event.userUuid) } returns user
                    every { preferenceService.isEnabled(user, NotificationType.CHAT_MESSAGE) } returns false

                    sut.on(event)

                    verify(exactly = 0) { outboxRepository.save(any()) }
                }
            }

            `when`("유저가 존재하고 알림 설정이 켜져 있으면") {
                then("outbox가 저장된다") {
                    val user = UserFixtures.user()
                    val event = event(user.uuid)
                    val template = mockk<NotificationTemplate> {
                        every { makeTitle(any(), any()) } returns "title"
                        every { makeBody(any(), any()) } returns "body"
                    }
                    every { userRepository.findByUuid(event.userUuid) } returns user
                    every { preferenceService.isEnabled(user, NotificationType.CHAT_MESSAGE) } returns true
                    every { templateFactory.templateOf(NotificationType.CHAT_MESSAGE) } returns template
                    every { outboxRepository.save(any()) } answers { firstArg() }

                    sut.on(event)

                    verify(exactly = 1) { outboxRepository.save(any()) }
                }
            }

            `when`("동일한 dedupKey를 가진 outbox row가 이미 존재하면") {
                then("중복 저장하지 않는다") {
                    val user = UserFixtures.user()
                    val event = NotificationEvent(
                        userUuid = user.uuid,
                        payload = NotificationPayload(type = NotificationType.CHAT_MESSAGE, dedupKey = "chat:room:receiver"),
                    )
                    every { userRepository.findByUuid(event.userUuid) } returns user
                    every { preferenceService.isEnabled(user, NotificationType.CHAT_MESSAGE) } returns true
                    every { outboxRepository.existsByDedupKey("chat:room:receiver") } returns true

                    sut.on(event)

                    verify(exactly = 0) { outboxRepository.save(any()) }
                }
            }
        }
    }
}
