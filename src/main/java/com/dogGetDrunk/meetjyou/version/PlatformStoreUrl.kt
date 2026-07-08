package com.dogGetDrunk.meetjyou.version

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "platform_store_url")
class PlatformStoreUrl(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "platform")
    val platform: Platform,

    var downloadUrl: String,
)
