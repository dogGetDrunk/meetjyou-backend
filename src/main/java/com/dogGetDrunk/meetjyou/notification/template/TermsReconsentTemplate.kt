package com.dogGetDrunk.meetjyou.notification.template

import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class TermsReconsentTemplate : NotificationTemplate {

    override fun makeTitle(locale: Locale, payload: NotificationPayload): String = "[약관 변경 안내]"

    override fun makeBody(locale: Locale, payload: NotificationPayload): String {
        val displayText = payload.bodyArgs["displayText"] ?: "약관"
        return "$displayText 이(가) 변경되었어요. 확인 후 다시 동의해주세요."
    }
}
