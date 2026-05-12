package com.dogGetDrunk.meetjyou.notification.template

import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class PartyCreatedTemplate : NotificationTemplate {
    override fun makeTitle(locale: Locale, payload: NotificationPayload): String = "[새 파티 생성]"
    override fun makeBody(locale: Locale, payload: NotificationPayload): String {
        val partyName = payload.bodyArgs["partyName"] ?: "파티"
        return "$partyName 파티가 생성되었어요."
    }
}
