package com.dogGetDrunk.meetjyou.notificationcenter

import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import com.dogGetDrunk.meetjyou.config.RestControllerV1
import com.dogGetDrunk.meetjyou.notificationcenter.dto.NoticesSectionResponse
import com.dogGetDrunk.meetjyou.notificationcenter.dto.ReceivedApplicationsSectionResponse
import com.dogGetDrunk.meetjyou.notificationcenter.dto.SentApplicationsSectionResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus

@RestControllerV1
@RequestMapping("/notification-center")
@Tag(name = "알림 센터 API", description = "공지사항, 받은 신청, 보낸 신청 조회 및 읽음 처리")
class NotificationCenterController(
    private val notificationCenterService: NotificationCenterService,
) {

    @Operation(summary = "공지사항 목록 조회", description = "전체 공지사항 목록과 읽지 않은 공지 수를 반환합니다.")
    @GetMapping("/notices")
    fun getNotices(
        @ParameterObject
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): NoticesSectionResponse {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        return notificationCenterService.getNotices(userUuid, pageable)
    }

    @Operation(summary = "공지사항 읽음 처리", description = "현재 시각을 기준으로 공지사항 읽음 시각을 갱신합니다.")
    @PostMapping("/notices/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markNoticesRead() {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        notificationCenterService.markNoticesRead(userUuid)
    }

    @Operation(summary = "받은 신청 목록 조회", description = "내가 파티장인 파티에 들어온 PENDING 신청 목록과 읽지 않은 수를 반환합니다.")
    @GetMapping("/received-applications")
    fun getReceivedApplications(
        @ParameterObject
        @PageableDefault(size = 20, sort = ["joinedAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): ReceivedApplicationsSectionResponse {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        return notificationCenterService.getReceivedApplications(userUuid, pageable)
    }

    @Operation(summary = "받은 신청 읽음 처리", description = "받은 신청 목록을 전체 읽음 처리합니다.")
    @PostMapping("/received-applications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markReceivedApplicationsRead() {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        notificationCenterService.markReceivedApplicationsRead(userUuid)
    }

    @Operation(summary = "보낸 신청 목록 조회", description = "내가 한 파티 신청 목록과 상태 변경 미확인 수를 반환합니다.")
    @GetMapping("/sent-applications")
    fun getSentApplications(
        @ParameterObject
        @PageableDefault(size = 20, sort = ["statusChangedAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): SentApplicationsSectionResponse {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        return notificationCenterService.getSentApplications(userUuid, pageable)
    }

    @Operation(summary = "보낸 신청 읽음 처리", description = "수락/거절 결과를 전체 읽음 처리합니다.")
    @PostMapping("/sent-applications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markSentApplicationsRead() {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        notificationCenterService.markSentApplicationsRead(userUuid)
    }
}
