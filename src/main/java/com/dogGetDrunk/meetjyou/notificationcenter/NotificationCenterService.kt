package com.dogGetDrunk.meetjyou.notificationcenter

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.notificationcenter.dto.ApplicationStatus
import com.dogGetDrunk.meetjyou.notificationcenter.dto.NoticeItem
import com.dogGetDrunk.meetjyou.notificationcenter.dto.NoticesSectionResponse
import com.dogGetDrunk.meetjyou.notificationcenter.dto.ReceivedApplicationItem
import com.dogGetDrunk.meetjyou.notificationcenter.dto.ReceivedApplicationsSectionResponse
import com.dogGetDrunk.meetjyou.notificationcenter.dto.SentApplicationItem
import com.dogGetDrunk.meetjyou.notificationcenter.dto.SentApplicationsSectionResponse
import com.dogGetDrunk.meetjyou.notice.NoticeRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class NotificationCenterService(
    private val userRepository: UserRepository,
    private val noticeRepository: NoticeRepository,
    private val userPartyRepository: UserPartyRepository,
    private val postRepository: PostRepository,
) {
    private val log = LoggerFactory.getLogger(NotificationCenterService::class.java)

    @Transactional(readOnly = true)
    fun getNotices(userUuid: UUID, pageable: Pageable): NoticesSectionResponse {
        val user = userRepository.findByUuid(userUuid) ?: throw UserNotFoundException(userUuid)
        val lastViewed = user.lastNoticesViewedAt
        val unreadCount = if (lastViewed == null) {
            noticeRepository.count().toInt()
        } else {
            noticeRepository.countByCreatedAtAfter(lastViewed).toInt()
        }
        val page = noticeRepository.findAllByOrderByCreatedAtDesc(pageable)
        val items = page.content
            .map { NoticeItem(uuid = it.uuid, title = it.title, body = it.body, createdAt = it.createdAt) }
        return NoticesSectionResponse(unreadCount = unreadCount, totalCount = page.totalElements, notices = items)
    }

    @Transactional
    fun markNoticesRead(userUuid: UUID) {
        val user = userRepository.findByUuid(userUuid) ?: throw UserNotFoundException(userUuid)
        user.lastNoticesViewedAt = Instant.now()
        log.info("Notices marked as read for user={}", userUuid)
    }

    @Transactional(readOnly = true)
    fun getReceivedApplications(userUuid: UUID, pageable: Pageable): ReceivedApplicationsSectionResponse {
        val allPending = userPartyRepository.findAllPendingRequestsForHost(userUuid)
        val unreadCount = allPending.count { !it.hostRead }
        val totalCount = allPending.size.toLong()
        val paged = allPending
            .drop(pageable.pageNumber * pageable.pageSize)
            .take(pageable.pageSize)
        val partyUuids = paged.map { it.party.uuid }
        val postByPartyUuid = postRepository.findAllByParty_UuidIn(partyUuids).associateBy { it.party.uuid }
        val items = paged.map { up ->
            ReceivedApplicationItem(
                userUuid = up.user.uuid,
                nickname = up.user.nickname,
                thumbImgUrl = up.user.resolveThumbImgUrl(),
                partyUuid = up.party.uuid,
                partyName = up.party.name,
                postUuid = postByPartyUuid[up.party.uuid]?.uuid,
                applicationNote = up.applicationNote,
                requestedAt = up.joinedAt,
                read = up.hostRead,
            )
        }
        return ReceivedApplicationsSectionResponse(unreadCount = unreadCount, totalCount = totalCount, applications = items)
    }

    @Transactional
    fun markReceivedApplicationsRead(userUuid: UUID) {
        val pending = userPartyRepository.findAllPendingRequestsForHost(userUuid)
        pending.filter { !it.hostRead }.forEach { it.markHostRead() }
        log.info("Received applications marked as read for host={}", userUuid)
    }

    @Transactional(readOnly = true)
    fun getSentApplications(userUuid: UUID, pageable: Pageable): SentApplicationsSectionResponse {
        val allApplications = userPartyRepository.findAllSentApplicationsByUserUuid(userUuid)
        val allItems = allApplications.map { up ->
            val status = when (up.memberStatus) {
                MemberStatus.JOINED    -> ApplicationStatus.ACCEPTED
                MemberStatus.REJECTED  -> ApplicationStatus.REJECTED
                else                   -> ApplicationStatus.PENDING
            }
            val read = when (up.memberStatus) {
                MemberStatus.JOINED, MemberStatus.REJECTED -> up.applicantRead
                else -> true
            }
            SentApplicationItem(
                partyUuid = up.party.uuid,
                partyName = up.party.name,
                postUuid = null,
                status = status,
                applicationNote = up.applicationNote,
                appliedAt = up.joinedAt,
                statusChangedAt = up.statusChangedAt,
                read = read,
            )
        }
        val pendingCount = allItems.count { it.status == ApplicationStatus.PENDING }
        val changedCount = allItems.count { it.status != ApplicationStatus.PENDING && !it.read }
        val totalCount = allItems.size.toLong()
        val paged = allItems
            .drop(pageable.pageNumber * pageable.pageSize)
            .take(pageable.pageSize)
        val partyUuids = paged.map { it.partyUuid }
        val postByPartyUuid = postRepository.findAllByParty_UuidIn(partyUuids).associateBy { it.party.uuid }
        val pagedWithPost = paged.map { it.copy(postUuid = postByPartyUuid[it.partyUuid]?.uuid) }
        return SentApplicationsSectionResponse(pendingCount = pendingCount, changedCount = changedCount, totalCount = totalCount, applications = pagedWithPost)
    }

    @Transactional
    fun markSentApplicationsRead(userUuid: UUID) {
        val applications = userPartyRepository.findAllSentApplicationsByUserUuid(userUuid)
        applications.filter { !it.applicantRead }.forEach { it.markApplicantRead() }
        log.info("Sent applications marked as read for user={}", userUuid)
    }
}
