package com.dogGetDrunk.meetjyou.user

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import org.hibernate.annotations.Check
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import java.sql.Types
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Entity
// Mirrors V38 so the H2 test schema (generated from entities) enforces it too.
@Check(name = "chk_user_withdrawn_at", constraints = "status = 'NORMAL' OR withdrawn_at IS NOT NULL")
class User(
    var email: String,
    var nickname: String,
    @Enumerated(EnumType.STRING)
    var authProvider: AuthProvider,
    var externalId: String,
    @Enumerated(EnumType.STRING)
    var role: Role = Role.USER,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0
    @Column(nullable = false, unique = true)
    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()
    var bio: String? = null
    var participation: Int = 0
    var hasProfileImage: Boolean = false
    var notified: Boolean = true
    var marketingSnsConsented: Boolean = false
    var marketingEmailConsented: Boolean = false
    var lastNoticesViewedAt: Instant? = null
    @CreationTimestamp
    val createdAt: Instant = Instant.now()
    val lastLoginAt: Instant = Instant.now()
    @UpdateTimestamp
    val updatedAt: Instant = Instant.now()
    @Enumerated(EnumType.STRING)
    var status: UserStatus = UserStatus.NORMAL
    var withdrawnAt: Instant? = null

    // Social identities this user can sign in with. Owned by the user: saved and removed with it.
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    val socialAccounts: MutableList<UserSocialAccount> = mutableListOf()

    fun addSocialAccount(provider: AuthProvider, externalId: String, email: String) {
        socialAccounts.add(UserSocialAccount(user = this, provider = provider, externalId = externalId, email = email))
    }

    fun isActive(): Boolean = status == UserStatus.NORMAL

    fun withdraw(now: Instant) {
        status = UserStatus.DELETED
        withdrawnAt = now
    }

    // Restorable only while the grace period is still running; afterwards the account waits for the purge job.
    fun isRestorable(now: Instant): Boolean {
        val withdrawn = withdrawnAt ?: return false
        return status == UserStatus.DELETED && withdrawn.isAfter(now.minus(WITHDRAWAL_GRACE_PERIOD))
    }

    fun restore() {
        status = UserStatus.NORMAL
        withdrawnAt = null
    }

    /**
     * Strips personal data but keeps the row, so posts, parties and chats that reference it stay
     * valid. Child rows (social accounts, tokens, preferences) are bulk-deleted by the caller.
     */
    fun anonymize() {
        email = ""
        nickname = anonymizedNickname(id)
        externalId = anonymizedExternalId(uuid)
        bio = null
        hasProfileImage = false
        notified = false
        marketingSnsConsented = false
        marketingEmailConsented = false
        status = UserStatus.PURGED
    }

    companion object {
        val WITHDRAWAL_GRACE_PERIOD: Duration = Duration.ofDays(30)

        // Sign-up nicknames cannot contain '~', so this never collides with a real one, and the
        // base-36 id keeps it unique and within the 10-character nickname column.
        private const val ANONYMIZED_NICKNAME_PREFIX = "~"
        private const val ANONYMIZED_NICKNAME_RADIX = 36
        private const val ANONYMIZED_EXTERNAL_ID_PREFIX = "withdrawn:"

        fun anonymizedNickname(id: Long): String =
            "$ANONYMIZED_NICKNAME_PREFIX${id.toString(ANONYMIZED_NICKNAME_RADIX)}"

        // Frees the legacy UNIQUE user.external_id so the same social identity can sign up again.
        fun anonymizedExternalId(uuid: UUID): String = "$ANONYMIZED_EXTERNAL_ID_PREFIX$uuid"
    }
}
