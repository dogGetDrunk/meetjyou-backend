package com.dogGetDrunk.meetjyou.notification.template

import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class NoticeTemplate : NotificationTemplate {

    override fun makeTitle(locale: Locale, payload: NotificationPayload): String = "[공지사항]"
    override fun makeBody(locale: Locale, payload: NotificationPayload): String {
        val title = payload.bodyArgs["title"] ?: "제목 없음"
        val content = payload.bodyArgs["content"] ?: "내용 없음"
        return "$title\n$content"
    }
}
