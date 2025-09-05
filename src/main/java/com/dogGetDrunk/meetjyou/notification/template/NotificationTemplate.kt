package com.dogGetDrunk.meetjyou.notification.template

import com.dogGetDrunk.meetjyou.notification.NotificationPayload
import java.util.Locale

interface NotificationTemplate {
    fun makeTitle(locale: Locale, payload: NotificationPayload): String
    fun makeBody(locale: Locale, payload: NotificationPayload): String
}
