package com.dogGetDrunk.meetjyou.version

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "app_version",
    uniqueConstraints = [UniqueConstraint(columnNames = ["version", "platform"])]
)
class AppVersion(
    @Enumerated(EnumType.STRING)
    val platform: Platform,

    var version: String,

    @Column(columnDefinition = "TINYINT(1) DEFAULT 0")
    var forceUpdate: Boolean,

    var message: String? = null,

    // Whether the store review for this version has actually completed and it is downloadable.
    // Only released versions are considered for /check's latest/minimum calculation.
    @Column(name = "store_released", columnDefinition = "TINYINT(1) DEFAULT 0")
    var storeReleased: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreationTimestamp
    val releasedAt: Instant = Instant.now()
}
