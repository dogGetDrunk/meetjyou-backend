package com.dogGetDrunk.meetjyou.notification.template

import com.dogGetDrunk.meetjyou.notification.NotificationType
import org.springframework.stereotype.Component

@Component
class NotificationTemplateFactory(
    private val noticeTemplate: NoticeTemplate,
    private val chatMessageTemplate: ChatMessageTemplate,
    private val partyCreatedTemplate: PartyCreatedTemplate,
    private val partyJoinRequestTemplate: PartyJoinRequestTemplate,
    private val partyJoinAcceptedTemplate: PartyJoinAcceptedTemplate,
    private val partyJoinRejectedTemplate: PartyJoinRejectedTemplate,
    private val partyMemberJoinedTemplate: PartyMemberJoinedTemplate,
    private val termsReconsentTemplate: TermsReconsentTemplate,
) {
    fun templateOf(type: NotificationType): NotificationTemplate = when (type) {
        NotificationType.NOTICE -> noticeTemplate
        NotificationType.CHAT_MESSAGE -> chatMessageTemplate
        NotificationType.PARTY_CREATED -> partyCreatedTemplate
        NotificationType.PARTY_JOIN_REQUEST -> partyJoinRequestTemplate
        NotificationType.PARTY_JOIN_ACCEPTED -> partyJoinAcceptedTemplate
        NotificationType.PARTY_JOIN_REJECTED -> partyJoinRejectedTemplate
        NotificationType.PARTY_MEMBER_JOINED -> partyMemberJoinedTemplate
        NotificationType.TERMS_RECONSENT -> termsReconsentTemplate
    }
}
