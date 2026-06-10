package com.dogGetDrunk.meetjyou.notificationcenter.support

import com.dogGetDrunk.meetjyou.notice.Notice
import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import java.time.Instant

object NotificationCenterFixtures {

    fun party(name: String = "Test Party"): Party = Party(
        itinStart = Instant.now(),
        itinFinish = Instant.now().plusSeconds(86400),
        destination = "Seoul",
        joined = 1,
        capacity = 5,
        name = name,
    )

    fun post(party: Party, author: User): Post = Post(
        party = party,
        isInstant = false,
        title = "Test Post",
        content = "Test content",
        itinStart = party.itinStart,
        itinFinish = party.itinFinish,
        location = party.destination,
        capacity = party.capacity,
    ).also { it.author = author }

    fun notice(title: String = "Notice Title", body: String = "Notice Body"): Notice =
        Notice(title = title, body = body)

    fun pendingUserParty(party: Party, user: User, applicationNote: String? = null): UserParty =
        UserParty(party, user, PartyRole.MEMBER).also {
            it.applicationNote = applicationNote
            it.pending()
        }

    fun hostUserParty(party: Party, host: User): UserParty =
        UserParty(party, host, PartyRole.HOST)
}
