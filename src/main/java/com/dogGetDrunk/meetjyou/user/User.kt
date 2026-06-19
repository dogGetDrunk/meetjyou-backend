package com.dogGetDrunk.meetjyou.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import java.sql.Types
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

@Entity
class User(
    val email: String,
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
    var imgUrl: String? = null
    var thumbImgUrl: String? = null
    var notified: Boolean = true
    var marketingConsented: Boolean = false
    var lastNoticesViewedAt: Instant? = null
    @CreationTimestamp
    val createdAt: Instant = Instant.now()
    val lastLoginAt: Instant = Instant.now()
    @UpdateTimestamp
    val updatedAt: Instant = Instant.now()
    @Enumerated(EnumType.STRING)
    var status: UserStatus = UserStatus.NORMAL
}
