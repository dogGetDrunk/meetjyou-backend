package com.dogGetDrunk.meetjyou.notification.push

import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.version.AppVersion
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.util.UUID

@Entity
class PushToken(
    val token: String,

    @Enumerated(EnumType.STRING)
    val platForm: PushPlatform,

    val deviceModel: String? = null,
    var isActive: Boolean = true,

    @ManyToOne(fetch = FetchType.LAZY)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    val appVersion: AppVersion,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()

    enum class PushPlatform { ANDROID, IOS, WEB }
}

