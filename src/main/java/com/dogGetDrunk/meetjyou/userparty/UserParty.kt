package com.dogGetDrunk.meetjyou.userparty

import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(uniqueConstraints = [UniqueConstraint(name = "uk_user_party", columnNames = ["user_id", "party_id"])])
class UserParty(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    val party: Party,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    val role: PartyRole,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
    @CreationTimestamp
    val joinedAt: Instant = Instant.now()
    @Enumerated(EnumType.STRING)
    var memberStatus = MemberStatus.JOINED
    @Column(nullable = false)
    var statusChangedAt: Instant = Instant.now()
    var lastReadMessageId: Long? = null
    @Column(length = 500)
    var applicationNote: String? = null
    var hostRead: Boolean = true
    var applicantRead: Boolean = true

    fun pending() { memberStatus = MemberStatus.PENDING;   statusChangedAt = Instant.now(); hostRead = false }
    fun approve() { memberStatus = MemberStatus.JOINED;    statusChangedAt = Instant.now(); applicantRead = false }
    fun reject()  { memberStatus = MemberStatus.REJECTED;  statusChangedAt = Instant.now(); applicantRead = false }
    fun ban()     { memberStatus = MemberStatus.BANNED;    statusChangedAt = Instant.now() }
    fun leave()   { memberStatus = MemberStatus.LEFT;      statusChangedAt = Instant.now() }
    fun markHostRead()      { hostRead = true }
    fun markApplicantRead() { applicantRead = true }

    fun isActiveMember(): Boolean = memberStatus == MemberStatus.JOINED

    fun updateLastReadMessageId(lastReadMessageId: Long) {
        if (this.lastReadMessageId == null || this.lastReadMessageId!! < lastReadMessageId) {
            this.lastReadMessageId = lastReadMessageId
        }
    }
}
