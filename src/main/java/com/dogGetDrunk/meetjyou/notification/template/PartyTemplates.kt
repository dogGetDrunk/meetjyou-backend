package com.dogGetDrunk.meetjyou.notification.template

import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class PartyJoinRequestTemplate : NotificationTemplate {

    override fun makeTitle(locale: Locale, payload: NotificationPayload): String {
        return "[파티 참여 요청]"
    }

    override fun makeBody(locale: Locale, payload: NotificationPayload): String {
        val applicant = payload.bodyArgs["applicant"] ?: "누군가"
        return "$applicant 님이 참여를 신청했어요."
    }
}

@Component
class PartyJoinAcceptedTemplate : NotificationTemplate {
    override fun makeTitle(locale: Locale, payload: NotificationPayload): String = "[파티 참여 신청 승인]"
    override fun makeBody(locale: Locale, payload: NotificationPayload): String = "참여 신청이 승인되었어요."
}

@Component
class PartyJoinRejectedTemplate : NotificationTemplate {
    override fun makeTitle(locale: Locale, payload: NotificationPayload): String = "[파티 참여 신청 거절]"
    override fun makeBody(locale: Locale, payload: NotificationPayload): String = "참여 신청이 거절되었어요."
}
