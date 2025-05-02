package com.dogGetDrunk.meetjyou.notice

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface NoticeRepository : JpaRepository<Notice, Long> {
    fun findByUuid(uuid: UUID): Notice?
    fun existsByUuid(uuid: UUID): Boolean
    fun deleteByUuid(uuid: UUID): Boolean
}
