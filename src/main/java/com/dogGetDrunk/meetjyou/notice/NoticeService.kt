package com.dogGetDrunk.meetjyou.notice

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.NoticeNotFoundException
import com.dogGetDrunk.meetjyou.notice.dto.NoticeRequest
import com.dogGetDrunk.meetjyou.notice.dto.NoticeResponse
import com.dogGetDrunk.meetjyou.notification.event.NoticeBroadcastEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NoticeService(
    private val noticeRepository: NoticeRepository,
    private val publisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(NoticeService::class.java)

    fun getAllNotices(): List<NoticeResponse> {
        return noticeRepository.findAllByOrderByCreatedAtDesc().map { NoticeResponse.from(it) }
    }

    fun getNoticeByUuid(uuid: UUID): NoticeResponse {
        return noticeRepository.findByUuid(uuid)?.let { NoticeResponse.from(it) }
            ?: throw NoticeNotFoundException(uuid)
    }

    @Transactional
    fun createNotice(request: NoticeRequest): NoticeResponse {
        val saved = noticeRepository.save(Notice(title = request.title, body = request.body))
        log.info("Notice created: uuid={}", saved.uuid)

        if (request.notify) {
            publisher.publishEvent(
                NoticeBroadcastEvent(
                    noticeUuid = saved.uuid,
                    noticeTitle = saved.title,
                    noticeBody = saved.body,
                    critical = request.critical,
                )
            )
        }

        return NoticeResponse.from(saved)
    }

    @Transactional
    fun updateNotice(uuid: UUID, request: NoticeRequest): NoticeResponse {
        val existing = noticeRepository.findByUuid(uuid) ?: throw NoticeNotFoundException(uuid)
        val updated = existing.apply {
            title = request.title
            body = request.body
        }
        noticeRepository.save(updated)

        log.info("공지사항 수정: {}", updated)
        return NoticeResponse.from(updated)
    }

    @Transactional
    fun deleteNotice(uuid: UUID) {
        if (!noticeRepository.existsByUuid(uuid)) {
            throw NoticeNotFoundException(uuid)
        }
        noticeRepository.deleteByUuid(uuid)
        log.info("공지사항 삭제: {}", uuid)
    }
}
