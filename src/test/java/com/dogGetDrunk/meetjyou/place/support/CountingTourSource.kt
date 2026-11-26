package com.dogGetDrunk.meetjyou.place.support

import com.dogGetDrunk.meetjyou.place.tour.TourPlace
import com.dogGetDrunk.meetjyou.place.tour.TourPlaceSource
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Enabled 관광공사 source that only counts calls, so a read or write path that reached for the API
 * would show up. Tests compare [calls] before and after their own requests: the bean is shared by
 * every test class that imports [CountingTourConfig] with the same context settings.
 */
class CountingTourSource : TourPlaceSource {
    override val enabled = true
    var calls = 0

    override fun searchKeyword(keyword: String, sidoCode: String?, size: Int): List<TourPlace> {
        calls++
        return emptyList()
    }
}

@TestConfiguration
class CountingTourConfig {
    @Bean
    @Primary
    fun countingTourSource(): CountingTourSource = CountingTourSource()
}
