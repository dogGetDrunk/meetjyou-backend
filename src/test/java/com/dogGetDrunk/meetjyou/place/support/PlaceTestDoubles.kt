package com.dogGetDrunk.meetjyou.place.support

import com.dogGetDrunk.meetjyou.place.PlaceRepository
import com.dogGetDrunk.meetjyou.place.Region
import com.dogGetDrunk.meetjyou.place.RegionCatalog
import com.dogGetDrunk.meetjyou.place.RegionLevel
import com.dogGetDrunk.meetjyou.place.RegionRepository
import com.dogGetDrunk.meetjyou.place.TravelDestinationResolver
import java.lang.reflect.Proxy

/**
 * Travel-destination collaborators for services built by hand in tests that are not about
 * destinations. Real catalog and resolver over a 미지정-only region list, so requests without
 * regionCode/placeId resolve to '00' (seeded in both H2 and Flyway schemas). Not mockk: many specs
 * call clearAllMocks() in beforeEach, which would wipe stubs.
 */
object PlaceTestDoubles {
    private val UNSPECIFIED = Region(Region.UNSPECIFIED_CODE, "미지정", "미지정", RegionLevel.SIDO, null, false)

    fun regionCatalog(): RegionCatalog = RegionCatalog(repository(RegionRepository::class.java) { listOf(UNSPECIFIED) })

    fun resolver(): TravelDestinationResolver =
        TravelDestinationResolver(regionCatalog(), repository(PlaceRepository::class.java) { emptyList<Any>() })

    // Only findAll() answers; any other repository call means the test needs a real fixture.
    private fun <T> repository(type: Class<T>, findAll: () -> List<Any>): T =
        type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
                when {
                    method.name == "findAll" && method.parameterCount == 0 -> findAll()
                    method.name == "toString" -> type.simpleName
                    method.name == "hashCode" -> System.identityHashCode(proxy)
                    method.name == "equals" -> proxy === args?.firstOrNull()
                    else -> throw UnsupportedOperationException("${type.simpleName}.${method.name} in a test double")
                }
            },
        )
}
