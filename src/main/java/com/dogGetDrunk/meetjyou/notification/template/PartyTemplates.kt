package com.dogGetDrunk.meetjyou.notification.template

import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class PartyJoinRequestTemplate : NotificationTemplate {
    override fun makeTitle(locale: Locale, payload: NotificationPayload): String = "[파티 참여 요청]"
    override fun makeBody(locale: Locale, payload: NotificationPayload): String {
        val applicant = payload.bodyArgs["applicant"] ?: "누군가"
        return "${applicant}님이 동행에 참여를 신청하셨어요."
    }
}

@Component
class PartyJoinAcceptedTemplate : NotificationTemplate {
    override fun makeTitle(locale: Locale, payload: NotificationPayload): String = "[파티 참여 신청 승인]"
    override fun makeBody(locale: Locale, payload: NotificationPayload): String = "동행 참여가 수락되었어요."
}

@Component
class PartyJoinRejectedTemplate : NotificationTemplate {
    override fun makeTitle(locale: Locale, payload: NotificationPayload): String = "[파티 참여 신청 거절]"
    override fun makeBody(locale: Locale, payload: NotificationPayload): String = "동행 참여가 거절되었어요."
}

@Component
class PartyMemberJoinedTemplate : NotificationTemplate {
    override fun makeTitle(locale: Locale, payload: NotificationPayload): String = "[새 파티 멤버]"
    override fun makeBody(locale: Locale, payload: NotificationPayload): String {
        val member = payload.bodyArgs["member"] ?: "누군가"
        return "${member}님이 동행에 참여하셨어요."
    }
}
