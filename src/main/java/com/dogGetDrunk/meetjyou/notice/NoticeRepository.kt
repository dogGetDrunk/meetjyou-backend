package com.dogGetDrunk.meetjyou.notice

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface NoticeRepository : JpaRepository<Notice, Long> {
    fun findByUuid(uuid: UUID): Notice?
    fun existsByUuid(uuid: UUID): Boolean
    fun deleteByUuid(uuid: UUID): Int
    fun findAllByOrderByCreatedAtDesc(): List<Notice>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Notice>
    fun countByCreatedAtAfter(createdAt: Instant): Long
}
