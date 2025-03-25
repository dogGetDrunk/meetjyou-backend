package com.dogGetDrunk.meetjyou.version

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "app_version")
class AppVersion(
    @Column(columnDefinition = "TINYINT(1) DEFAULT 1")
    var forceUpdate: Boolean,

    var version: String,
    var downloadUrl: String
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreationTimestamp
    val releasedAt: LocalDateTime = LocalDateTime.now()
}
