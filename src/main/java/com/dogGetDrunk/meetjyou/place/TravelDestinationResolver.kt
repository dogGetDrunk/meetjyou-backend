package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/**
 * A representative travel destination: a region (미지정 when none was given) and at most one place,
 * either a store ([place]) or a 관광공사 place known only by its [tourContentId] (ADR-0004: never stored).
 */
data class TravelDestination(
    val regionCode: String,
    val place: Place?,
    val tourContentId: String? = null,
) {
    companion object {
        const val TOUR_CONTENT_ID_MAX_LENGTH = 20
        val UNSPECIFIED = TravelDestination(Region.UNSPECIFIED_CODE, null)
    }
}

/**
 * Turns the `regionCode`/`placeId`/`tourContentId` of a write request into a destination. All are
 * optional while the app still sends only the free-text location.
 */
@Component
class TravelDestinationResolver(
    private val regionCatalog: RegionCatalog,
    private val placeRepository: PlaceRepository,
) {
    /**
     * [current] is what the edited row already holds: re-sending it unchanged is accepted even when a
     * later import moved the place to another district.
     *
     * A [tourContentId] is checked for shape only: no 관광공사 call, so neither its existence nor its
     * region is verified (accepted in the task intent).
     */
    fun resolve(
        regionCode: String?,
        placeId: Long?,
        tourContentId: String? = null,
        current: TravelDestination? = null,
    ): TravelDestination {
        if (current != null && isUnchanged(current, regionCode, placeId, tourContentId)) {
            return current
        }
        validateSinglePlace(placeId, tourContentId)
        val place = placeId?.let { findPlace(it) }
        val region = regionCode?.let { validateRegion(it) }
        val tourId = tourContentId?.let { validateTourContentId(it) }
        return when {
            place != null && region != null -> TravelDestination(validatePlaceInRegion(place, region), place)
            place != null -> TravelDestination(place.regionCode, place)
            else -> TravelDestination(region ?: Region.UNSPECIFIED_CODE, null, tourId)
        }
    }

    private fun isUnchanged(
        current: TravelDestination,
        regionCode: String?,
        placeId: Long?,
        tourContentId: String?,
    ): Boolean =
        regionCode == current.regionCode && placeId == current.place?.id && tourContentId == current.tourContentId

    private fun validateSinglePlace(placeId: Long?, tourContentId: String?) {
        if (placeId != null && tourContentId != null) {
            throw InvalidInputException(
                value = "$placeId/$tourContentId",
                message = "Choose either a store place or a tour place, not both",
            )
        }
    }

    private fun validateTourContentId(tourContentId: String): String {
        if (!TOUR_CONTENT_ID_PATTERN.matches(tourContentId)) {
            throw InvalidInputException(value = tourContentId, message = "Invalid tour content id")
        }
        return tourContentId
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

private val TOUR_CONTENT_ID_PATTERN = Regex("^[0-9]{1,${TravelDestination.TOUR_CONTENT_ID_MAX_LENGTH}}$")
