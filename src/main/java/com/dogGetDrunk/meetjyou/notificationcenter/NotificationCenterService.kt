package com.dogGetDrunk.meetjyou.notificationcenter

import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notificationcenter.dto.ApplicationStatus
import com.dogGetDrunk.meetjyou.notificationcenter.dto.NoticeItem
import com.dogGetDrunk.meetjyou.notificationcenter.dto.NoticesSectionResponse
import com.dogGetDrunk.meetjyou.notificationcenter.dto.ReceivedApplicationItem
import com.dogGetDrunk.meetjyou.notificationcenter.dto.ReceivedApplicationsSectionResponse
import com.dogGetDrunk.meetjyou.notificationcenter.dto.SentApplicationItem
import com.dogGetDrunk.meetjyou.notificationcenter.dto.SentApplicationsSectionResponse
import com.dogGetDrunk.meetjyou.notice.NoticeRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class NotificationCenterService(
    private val noticeRepository: NoticeRepository,
    private val userPartyRepository: UserPartyRepository,
    private val postRepository: PostRepository,
    private val currentUserProvider: CurrentUserProvider,
) {
    private val log = LoggerFactory.getLogger(NotificationCenterService::class.java)

    @Transactional(readOnly = true)
    fun getNotices(pageable: Pageable): NoticesSectionResponse {
        val user = currentUserProvider.user
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
    fun markNoticesRead() {
        val user = currentUserProvider.user
        user.lastNoticesViewedAt = Instant.now()
        log.info("Notices marked as read for user={}", user.uuid)
    }

    @Transactional(readOnly = true)
    fun getReceivedApplications(pageable: Pageable): ReceivedApplicationsSectionResponse {
        val userUuid = currentUserProvider.uuid
        val unreadCount = userPartyRepository.countUnreadPendingRequestsForHost(userUuid)
        val page = userPartyRepository.findAllPendingRequestsForHost(userUuid, pageable)
        val partyUuids = page.content.map { it.party.uuid }
        val postByPartyUuid = postRepository.findAllByParty_UuidIn(partyUuids).associateBy { it.party.uuid }
        val items = page.content.map { up ->
            ReceivedApplicationItem(
                userUuid = up.user.uuid,
                nickname = up.user.nickname,
                hasProfileImage = up.user.hasProfileImage,
                partyUuid = up.party.uuid,
                partyName = up.party.name,
                postUuid = postByPartyUuid[up.party.uuid]?.uuid,
                applicationNote = up.applicationNote,
                requestedAt = up.joinedAt,
                read = up.hostRead,
            )
        }
        return ReceivedApplicationsSectionResponse(unreadCount = unreadCount.toInt(), totalCount = page.totalElements, applications = items)
    }

    @Transactional
    fun markReceivedApplicationsRead() {
        val userUuid = currentUserProvider.uuid
        val pending = userPartyRepository.findAllPendingRequestsForHost(userUuid)
        pending.filter { !it.hostRead }.forEach { it.markHostRead() }
        log.info("Received applications marked as read for host={}", userUuid)
    }

    @Transactional(readOnly = true)
    fun getSentApplications(pageable: Pageable): SentApplicationsSectionResponse {
        val userUuid = currentUserProvider.uuid
        val pendingCount = userPartyRepository.countPendingSentApplications(userUuid)
        val changedCount = userPartyRepository.countChangedUnreadSentApplications(userUuid)
        val page = userPartyRepository.findAllSentApplicationsByUserUuid(userUuid, pageable)

        val items = page.content.map { up ->
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
        val partyUuids = items.map { it.partyUuid }
        val postByPartyUuid = postRepository.findAllByParty_UuidIn(partyUuids).associateBy { it.party.uuid }
        val itemsWithPost = items.map { it.copy(postUuid = postByPartyUuid[it.partyUuid]?.uuid) }
        return SentApplicationsSectionResponse(
            pendingCount = pendingCount.toInt(),
            changedCount = changedCount.toInt(),
            totalCount = page.totalElements,
            applications = itemsWithPost,
        )
    }

    @Transactional
    fun markSentApplicationsRead() {
        val userUuid = currentUserProvider.uuid
        val applications = userPartyRepository.findAllSentApplicationsByUserUuid(userUuid)
        applications.filter { !it.applicantRead }.forEach { it.markApplicantRead() }
        log.info("Sent applications marked as read for user={}", userUuid)
    }
}
