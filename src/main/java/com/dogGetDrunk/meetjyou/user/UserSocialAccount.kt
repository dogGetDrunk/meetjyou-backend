package com.dogGetDrunk.meetjyou.user

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

/**
 * One social identity a user can sign in with. (provider, externalId) identifies the identity
 * across the whole service; a user holds at most one identity per provider.
 */
@Entity
@Table(
    name = "user_social_account",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_social_account_provider_external", columnNames = ["provider", "external_id"]),
        UniqueConstraint(name = "uk_social_account_user_provider", columnNames = ["user_id", "provider"]),
    ],
)
class UserSocialAccount(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val provider: AuthProvider,

    @Column(name = "external_id", nullable = false, length = 100)
    val externalId: String,

    // The email this provider verified, null if it shared none; user.email stays the account's contact email.
    val email: String?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}
