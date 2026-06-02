package com.dogGetDrunk.meetjyou.version

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AppVersionRepository : JpaRepository<AppVersion, Long> {
    fun findAllByPlatform(platform: Platform): List<AppVersion>
    fun findByVersionAndPlatform(version: String, platform: Platform): AppVersion?
}
