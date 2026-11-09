package com.dogGetDrunk.meetjyou.place

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import org.hibernate.annotations.Immutable

/**
 * 시·도 or 시·군·구, keyed by its 법정동 code. Rows only change through Flyway migrations, so the
 * app reads them once through [RegionCatalog] instead of joining this table.
 */
@Entity
@Immutable
class Region(
    @Id
    @Column(length = CODE_MAX_LENGTH)
    val code: String,
    @Column(nullable = false, length = 30)
    val name: String,
    @Column(nullable = false, length = 60)
    val fullName: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val level: RegionLevel,
    @Column(length = CODE_MAX_LENGTH)
    val parentCode: String?,
    @Column(nullable = false)
    val searchable: Boolean,
) {
    companion object {
        const val CODE_MAX_LENGTH = 5
        const val SIDO_CODE_LENGTH = 2
        const val UNSPECIFIED_CODE = "00"
    }
}

enum class RegionLevel {
    SIDO,
    SIGUNGU,
}
