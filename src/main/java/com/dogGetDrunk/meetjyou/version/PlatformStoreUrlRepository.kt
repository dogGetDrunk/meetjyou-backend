package com.dogGetDrunk.meetjyou.version

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PlatformStoreUrlRepository : JpaRepository<PlatformStoreUrl, Platform>
