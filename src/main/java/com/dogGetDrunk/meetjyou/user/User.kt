package com.dogGetDrunk.meetjyou.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
class User(
    @Column(columnDefinition = "TINYINT(1) DEFAULT 1")
    var notified: Boolean = true,

    var email: String,
    var nickname: String,
    var bio: String? = null,
    var birthDate: LocalDate? = null,
    var imgUrl: String? = null,
    var thumbImgUrl: String? = null,

    @Enumerated(EnumType.STRING)
    var authProvider: AuthProvider,

    @Enumerated(EnumType.STRING)
    var status: UserStatus = UserStatus.NORMAL,

    @Enumerated(EnumType.STRING)
    var role: Role = Role.USER,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.now()

    @UpdateTimestamp
    val lastLoginAt: LocalDateTime = LocalDateTime.now()

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.now()
}
