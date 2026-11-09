package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.place.Place
import com.dogGetDrunk.meetjyou.place.Region
import com.dogGetDrunk.meetjyou.place.TravelDestination
import com.dogGetDrunk.meetjyou.plan.Plan
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

// Dynamic so a write path that loaded the party without the row lock (image state, plan link)
// only updates the columns it changed instead of reverting a concurrent `joined` or `name` change.
@Entity
@DynamicUpdate
class Party(
    var itinStart: Instant,
    var itinFinish: Instant,
    var destination: String,
    var joined: Int,
    var capacity: Int,
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
    @Column(nullable = false, unique = true)
    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()
    @CreationTimestamp
    var createdAt: Instant = Instant.now()
    @UpdateTimestamp
    var lastEditedAt: Instant = Instant.now()
    @Enumerated(EnumType.STRING)
    var progressStatus = PartyProgressStatus.PLANNING
    @Enumerated(EnumType.STRING)
    var recruitmentStatus = PartyRecruitmentStatus.OPEN
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = true)
    var plan: Plan? = null
    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    var imageState: PartyImageState = PartyImageState.INHERITED
    @Column(columnDefinition = "TEXT")
    var planSnapshot: String? = null
    // Representative travel destination (region + optional place). '00' (미지정) until one is chosen.
    @Column(name = "region_code", nullable = false, length = Region.CODE_MAX_LENGTH)
    var regionCode: String = Region.UNSPECIFIED_CODE
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    var place: Place? = null

    companion object {
        const val NAME_MAX_LENGTH = 20
    }

    fun complete() {
        progressStatus = PartyProgressStatus.COMPLETED
        recruitmentStatus = PartyRecruitmentStatus.CLOSED
    }

    // Plain assignment: the caller holds the party row lock and validates capacity against joined.
    fun syncFromPost(
        capacity: Int,
        itinStart: Instant,
        itinFinish: Instant,
        destination: String,
        travelDestination: TravelDestination,
    ) {
        this.capacity = capacity
        this.itinStart = itinStart
        this.itinFinish = itinFinish
        this.destination = destination
        applyTravelDestination(travelDestination)
    }

    fun travelDestination(): TravelDestination = TravelDestination(regionCode, place)

    fun applyTravelDestination(destination: TravelDestination) {
        regionCode = destination.regionCode
        place = destination.place
    }

    fun closeRecruitment() {
        recruitmentStatus = PartyRecruitmentStatus.CLOSED
    }

    fun openRecruitment() {
        recruitmentStatus = PartyRecruitmentStatus.OPEN
    }
}
