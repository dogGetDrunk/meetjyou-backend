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
    fun getNotices(userUuid: UUID): NoticesSectionResponse {
        val user = userRepository.findByUuid(userUuid) ?: throw UserNotFoundException(userUuid)
        val notices = noticeRepository.findAll()
        val lastViewed = user.lastNoticesViewedAt
        val unreadCount = if (lastViewed == null) {
            notices.size
        } else {
            notices.count { it.createdAt.isAfter(lastViewed) }
        }
        val items = notices
            .sortedByDescending { it.createdAt }
            .map { NoticeItem(uuid = it.uuid, title = it.title, body = it.body, createdAt = it.createdAt) }
        return NoticesSectionResponse(unreadCount = unreadCount, notices = items)
    }

    @Transactional
    fun markNoticesRead(userUuid: UUID) {
        val user = userRepository.findByUuid(userUuid) ?: throw UserNotFoundException(userUuid)
        user.lastNoticesViewedAt = Instant.now()
        log.info("Notices marked as read for user={}", userUuid)
    }

    @Transactional(readOnly = true)
    fun getReceivedApplications(userUuid: UUID): ReceivedApplicationsSectionResponse {
        val pending = userPartyRepository.findAllPendingRequestsForHost(userUuid)
        val partyUuids = pending.map { it.party.uuid }
        val postByPartyUuid = postRepository.findAllByParty_UuidIn(partyUuids).associateBy { it.party.uuid }
        val items = pending.map { up ->
            ReceivedApplicationItem(
                userUuid = up.user.uuid,
                nickname = up.user.nickname,
                thumbImgUrl = up.user.thumbImgUrl,
                partyUuid = up.party.uuid,
                partyName = up.party.name,
                postUuid = postByPartyUuid[up.party.uuid]?.uuid,
                applicationNote = up.applicationNote,
                requestedAt = up.joinedAt,
                read = up.hostRead,
            )
        }
        val unreadCount = items.count { !it.read }
        return ReceivedApplicationsSectionResponse(unreadCount = unreadCount, applications = items)
    }

    @Transactional
    fun markReceivedApplicationsRead(userUuid: UUID) {
        val pending = userPartyRepository.findAllPendingRequestsForHost(userUuid)
        pending.filter { !it.hostRead }.forEach { it.markHostRead() }
        log.info("Received applications marked as read for host={}", userUuid)
    }

    @Transactional(readOnly = true)
    fun getSentApplications(userUuid: UUID): SentApplicationsSectionResponse {
        val applications = userPartyRepository.findAllSentApplicationsByUserUuid(userUuid)
        val partyUuids = applications.map { it.party.uuid }
        val postByPartyUuid = postRepository.findAllByParty_UuidIn(partyUuids).associateBy { it.party.uuid }
        val items = applications.map { up ->
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
                postUuid = postByPartyUuid[up.party.uuid]?.uuid,
                status = status,
                applicationNote = up.applicationNote,
                appliedAt = up.joinedAt,
                statusChangedAt = up.statusChangedAt,
                read = read,
            )
        }
        val pendingCount = items.count { it.status == ApplicationStatus.PENDING }
        val changedCount = items.count { it.status != ApplicationStatus.PENDING && !it.read }
        return SentApplicationsSectionResponse(pendingCount = pendingCount, changedCount = changedCount, applications = items)
    }

    @Transactional
    fun markSentApplicationsRead(userUuid: UUID) {
        val applications = userPartyRepository.findAllSentApplicationsByUserUuid(userUuid)
        applications.filter { !it.applicantRead }.forEach { it.markApplicantRead() }
        log.info("Sent applications marked as read for user={}", userUuid)
    }
}
