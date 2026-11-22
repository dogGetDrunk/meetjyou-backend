package com.dogGetDrunk.meetjyou.place.tour

/**
 * The tour API call failed. Messages never carry the service key or a request URI: Spring's own
 * client exceptions put the full URI (key included) into their message, so they are never wrapped.
 */
class TourApiException(
    val reason: Reason,
    message: String,
) : RuntimeException(message) {
    enum class Reason {
        UNREACHABLE,
        HTTP_STATUS,
        BAD_BODY,
        RESULT_CODE,
    }
}
