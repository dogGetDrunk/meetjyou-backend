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
import java.time.LocalDateTime
import java.util.UUID

@Entity
class User(
    val email: String,
    var nickname: String,
    var birthDate: LocalDate,
    @Enumerated(EnumType.STRING)
    var authProvider: AuthProvider,
    @Enumerated(EnumType.STRING)
    val role: Role = Role.USER,
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
    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.now()
    val lastLoginAt: LocalDateTime = LocalDateTime.now()
    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.now()
    @Enumerated(EnumType.STRING)
    var status: UserStatus = UserStatus.NORMAL
}
