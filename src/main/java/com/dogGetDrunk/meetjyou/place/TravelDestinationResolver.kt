package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/** A representative travel destination: a region (미지정 when none was given) and an optional place. */
data class TravelDestination(
    val regionCode: String,
    val place: Place?,
) {
    companion object {
        val UNSPECIFIED = TravelDestination(Region.UNSPECIFIED_CODE, null)
    }
}

/**
 * Turns the `regionCode`/`placeId` pair of a write request into a destination. Both are optional
 * while the app still sends only the free-text location.
 */
@Component
class TravelDestinationResolver(
    private val regionCatalog: RegionCatalog,
    private val placeRepository: PlaceRepository,
) {
    /**
     * [current] is what the edited row already holds: re-sending it unchanged is accepted even when a
     * later import moved the place to another district.
     */
    fun resolve(regionCode: String?, placeId: Long?, current: TravelDestination? = null): TravelDestination {
        if (current != null && regionCode == current.regionCode && placeId == current.place?.id) {
            return current
        }
        val place = placeId?.let { findPlace(it) }
        val region = regionCode?.let { validateRegion(it) }
        return when {
            place != null && region != null -> TravelDestination(validatePlaceInRegion(place, region), place)
            place != null -> TravelDestination(place.regionCode, place)
            region != null -> TravelDestination(region, null)
            else -> TravelDestination.UNSPECIFIED
        }
    }

    private fun findPlace(placeId: Long): Place =
        placeRepository.findByIdOrNull(placeId)
            ?: throw InvalidInputException(value = placeId.toString(), message = "Unknown place")

    private fun validateRegion(code: String): String {
        val region = regionCatalog.find(code)
        if (region == null || !region.searchable) {
            throw InvalidInputException(value = code, message = "Unknown region code")
        }
        return region.code
    }

    private fun validatePlaceInRegion(place: Place, regionCode: String): String {
        if (!regionCatalog.isWithin(place.regionCode, regionCode)) {
            throw InvalidInputException(
                value = "$regionCode/${place.id}",
                message = "Place ${place.id} is not in region $regionCode",
            )
        }
        return regionCode
    }
}
