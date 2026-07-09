package com.dogGetDrunk.meetjyou.notice

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.NoticeNotFoundException
import com.dogGetDrunk.meetjyou.notice.dto.NoticeRequest
import com.dogGetDrunk.meetjyou.notification.event.NoticeBroadcastEvent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class NoticeServiceTest : BehaviorSpec() {
    private val noticeRepository = mockk<NoticeRepository>()
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val sut = NoticeService(noticeRepository, publisher)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }
        afterSpec { unmockkAll() }

        given("공지사항 목록을 페이지 단위로 조회하면") {
            `when`("getAllNotices를 호출하면") {
                then("최신순으로 정렬된 응답 페이지를 반환한다") {
                    val pageable = PageRequest.of(0, 10)
                    val notice = Notice(title = "제목", body = "본문")
                    every { noticeRepository.findAllByOrderByCreatedAtDesc(pageable) } returns PageImpl(listOf(notice))

                    val result = sut.getAllNotices(pageable)

                    result.content.map { it.uuid } shouldBe listOf(notice.uuid.toString())
                }
            }
        }

        given("존재하는 공지사항 uuid로 조회하면") {
            `when`("getNoticeByUuid를 호출하면") {
                then("공지사항 응답을 반환한다") {
                    val notice = Notice(title = "제목", body = "본문")
                    every { noticeRepository.findByUuid(notice.uuid) } returns notice

                    val result = sut.getNoticeByUuid(notice.uuid)

                    result.uuid shouldBe notice.uuid.toString()
                    result.title shouldBe "제목"
                }
            }
        }

        given("존재하지 않는 공지사항 uuid로 조회하면") {
            `when`("getNoticeByUuid를 호출하면") {
                then("NoticeNotFoundException을 던진다") {
                    val uuid = UUID.randomUUID()
                    every { noticeRepository.findByUuid(uuid) } returns null

                    shouldThrow<NoticeNotFoundException> {
                        sut.getNoticeByUuid(uuid)
                    }
                }
            }
        }

        given("notify=true로 공지사항을 생성하면") {
            `when`("createNotice를 호출하면") {
                then("NoticeBroadcastEvent를 발행한다") {
                    val request = NoticeRequest(title = "제목", body = "본문", notify = true, critical = true)
                    val savedSlot = slot<Notice>()
                    every { noticeRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

                    sut.createNotice(request)

                    val eventSlot = slot<NoticeBroadcastEvent>()
                    verify(exactly = 1) { publisher.publishEvent(capture(eventSlot)) }
                    eventSlot.captured.critical shouldBe true
                    eventSlot.captured.noticeTitle shouldBe "제목"
                }
            }
        }

        given("notify=false로 공지사항을 생성하면") {
            `when`("createNotice를 호출하면") {
                then("이벤트를 발행하지 않는다") {
                    val request = NoticeRequest(title = "제목", body = "본문", notify = false)
                    every { noticeRepository.save(any()) } answers { firstArg() }

                    sut.createNotice(request)

                    verify(exactly = 0) { publisher.publishEvent(any()) }
                }
            }
        }

        given("존재하는 공지사항을 수정하면") {
            `when`("updateNotice를 호출하면") {
                then("제목과 본문이 갱신된 응답을 반환한다") {
                    val notice = Notice(title = "old", body = "old body")
                    every { noticeRepository.findByUuid(notice.uuid) } returns notice
                    every { noticeRepository.save(any()) } answers { firstArg() }

                    val result = sut.updateNotice(notice.uuid, NoticeRequest(title = "new", body = "new body"))

                    result.title shouldBe "new"
                    result.body shouldBe "new body"
                }
            }
        }

        given("존재하지 않는 공지사항을 수정하려 하면") {
            `when`("updateNotice를 호출하면") {
                then("NoticeNotFoundException을 던진다") {
                    val uuid = UUID.randomUUID()
                    every { noticeRepository.findByUuid(uuid) } returns null

                    shouldThrow<NoticeNotFoundException> {
                        sut.updateNotice(uuid, NoticeRequest(title = "new", body = "new body"))
                    }
                }
            }
        }

        given("존재하는 공지사항을 삭제하면") {
            `when`("deleteNotice를 호출하면") {
                then("정상적으로 삭제된다") {
                    val uuid = UUID.randomUUID()
                    every { noticeRepository.existsByUuid(uuid) } returns true
                    every { noticeRepository.deleteByUuid(uuid) } returns 1

                    sut.deleteNotice(uuid)

                    verify(exactly = 1) { noticeRepository.deleteByUuid(uuid) }
                }
            }
        }

        given("존재하지 않는 공지사항을 삭제하려 하면") {
            `when`("deleteNotice를 호출하면") {
                then("NoticeNotFoundException을 던진다") {
                    val uuid = UUID.randomUUID()
                    every { noticeRepository.existsByUuid(uuid) } returns false

                    shouldThrow<NoticeNotFoundException> {
                        sut.deleteNotice(uuid)
                    }

                    verify(exactly = 0) { noticeRepository.deleteByUuid(any()) }
                }
            }
        }
    }
}
