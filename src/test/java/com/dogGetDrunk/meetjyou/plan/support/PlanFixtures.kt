package com.dogGetDrunk.meetjyou.plan.support

import com.dogGetDrunk.meetjyou.plan.Marker
import com.dogGetDrunk.meetjyou.plan.Plan
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import java.time.Instant

object PlanFixtures {
    fun plan(owner: com.dogGetDrunk.meetjyou.user.User = UserFixtures.user()) = Plan(
        itinStart = Instant.parse("2026-05-01T00:00:00Z"),
        itinFinish = Instant.parse("2026-05-05T00:00:00Z"),
        destination = "Seoul",
        centerLat = 37.5665,
        centerLng = 126.9780,
        owner = owner,
    )

    fun marker(plan: Plan, dayNum: Int = 1, idx: Int = 0) = Marker(
        lat = 37.5665,
        lng = 126.9780,
        date = Instant.parse("2026-05-01T10:00:00Z"),
        dayNum = dayNum,
        idx = idx,
        place = "Gyeongbokgung",
        memo = null,
        plan = plan,
    )
}
