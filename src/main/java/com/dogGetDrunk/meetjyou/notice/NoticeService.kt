package com.dogGetDrunk.meetjyou.notice

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.NoticeNotFoundException
import com.dogGetDrunk.meetjyou.notice.dto.NoticeRequest
import com.dogGetDrunk.meetjyou.notice.dto.NoticeResponse
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NoticeService(
    private val noticeRepository: NoticeRepository,
) {
    private val log = LoggerFactory.getLogger(NoticeService::class.java)

    fun getAllNotices(): List<NoticeResponse> {
        return noticeRepository.findAll().map { NoticeResponse.from(it) }
    }

    fun getNoticeById(id: Long): NoticeResponse {
        return noticeRepository.findByIdOrNull(id)?.let { NoticeResponse.from(it) }
            ?: throw NoticeNotFoundException(id)
    }

    @Transactional
    fun createNotice(request: NoticeRequest): NoticeResponse {
        val saved = noticeRepository.save(Notice(title = request.title, body = request.body))
        log.info("공지사항 생성: {}", saved)
        return NoticeResponse.from(saved)
    }

    @Transactional
    fun updateNotice(id: Long, request: NoticeRequest): NoticeResponse {
        val existing = noticeRepository.findByIdOrNull(id) ?: throw NoticeNotFoundException(id)
        val updated = existing.apply {
            title = request.title
            body = request.body
        }
        noticeRepository.save(updated)

        log.info("공지사항 수정: {}", updated)
        return NoticeResponse.from(updated)
    }

    @Transactional
    fun deleteNotice(id: Long) {
        if (!noticeRepository.existsById(id)) {
            throw NoticeNotFoundException(id)
        }
        noticeRepository.deleteById(id)
        log.info("공지사항 삭제: {}", id)
    }
}
