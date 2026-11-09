package com.dogGetDrunk.meetjyou.place

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * A store or attraction users can pick as a travel destination. Written by [PlaceImportService] with
 * plain SQL; the entity is the read side. Rows are never deleted: one that left the source file
 * turns inactive so posts pointing at it can still show it. One constructor parameter per column.
 */
@Suppress("LongParameterList")
@Entity
@Table(uniqueConstraints = [UniqueConstraint(name = "uk_place_source_key", columnNames = ["source", "source_key"])])
class Place(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val source: PlaceSource,
    @Column(nullable = false, length = 30)
    val sourceKey: String,
    @Column(nullable = false, length = 200)
    val name: String,
    @Column(length = 100)
    val branchName: String?,
    @Column(nullable = false, length = 300)
    val searchName: String,
    @Column(nullable = false, length = 10)
    val categoryCode: String,
    @Column(nullable = false, length = 50)
    val categoryName: String,
    @Column(nullable = false, length = Region.CODE_MAX_LENGTH)
    val regionCode: String,
    @Column(nullable = false, length = 200)
    val roadAddress: String,
    @Column(nullable = false)
    val lat: Double,
    @Column(nullable = false)
    val lng: Double,
    @Column(nullable = false)
    val active: Boolean,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}

enum class PlaceSource {
    // 소상공인시장진흥공단 상가(상권)정보
    SBIZ,
}
