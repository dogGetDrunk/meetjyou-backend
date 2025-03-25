package com.dogGetDrunk.meetjyou.version

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AppVersionRepository : JpaRepository<AppVersion?, Long?> {
    fun findFirstByOrderByReleasedAtDesc(): AppVersion?
    fun findByVersion(version: String): AppVersion?
}
