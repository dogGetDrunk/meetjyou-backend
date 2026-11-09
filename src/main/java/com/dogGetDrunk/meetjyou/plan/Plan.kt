package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.place.Place
import com.dogGetDrunk.meetjyou.place.Region
import com.dogGetDrunk.meetjyou.place.TravelDestination
import com.dogGetDrunk.meetjyou.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

@Entity
class Plan(
    @Column(length = 20)
    var title: String,
    var itinStart: Instant,
    var itinFinish: Instant,
    var destination: String,
    var centerLat: Double,
    var centerLng: Double,
    var favorite: Boolean = false,

    @Column(length = 500)
    var memo: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    var owner: User
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false, unique = true)
    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()

    @CreationTimestamp
    val createdAt: Instant = Instant.now()

    @UpdateTimestamp
    val updatedAt: Instant = Instant.now()

    // Representative travel destination (region + optional place). '00' (미지정) until one is chosen.
    @Column(name = "region_code", nullable = false, length = Region.CODE_MAX_LENGTH)
    var regionCode: String = Region.UNSPECIFIED_CODE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    var place: Place? = null

    fun travelDestination(): TravelDestination = TravelDestination(regionCode, place)

    fun applyTravelDestination(destination: TravelDestination) {
        regionCode = destination.regionCode
        place = destination.place
    }
}
