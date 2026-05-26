package com.dogGetDrunk.meetjyou.notification.template

import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class NoticeTemplate : NotificationTemplate {

    override fun makeTitle(locale: Locale, payload: NotificationPayload): String = "[공지사항]"
    override fun makeBody(locale: Locale, payload: NotificationPayload): String = "새로운 공지사항이 있어요."
}
