package com.dogGetDrunk.meetjyou.post

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import com.dogGetDrunk.meetjyou.plan.Plan
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PostRepository : JpaRepository<Post, Long> {
    fun findByUuid(uuid: UUID): Post?
    fun findByParty_Uuid(partyUuid: UUID): Post?
    fun findAllByParty_UuidIn(partyUuids: Collection<UUID>): List<Post>
    override fun findAll(pageable: Pageable): Page<Post>
    fun existsByUuidAndAuthor_Uuid(uuid: UUID, authorUuid: UUID): Boolean
    fun existsByPlan_UuidAndIsPlanPublicTrue(planUuid: UUID): Boolean
    fun findAllByPlan_UuidIn(planUuids: Collection<UUID>): List<Post>

    // Bulk on purpose: an UPDATE reads the latest committed row, so a post re-linked to another
    // plan after the caller's snapshot is skipped instead of being overwritten (#201).
    @Modifying
    @Query("UPDATE Post p SET p.plan = null, p.isPlanPublic = null WHERE p.plan = :plan")
    fun detachPlan(@Param("plan") plan: Plan): Int

    @Query(
        value = "SELECT p FROM Post p LEFT JOIN FETCH p.author LEFT JOIN FETCH p.party LEFT JOIN FETCH p.plan WHERE p.author.uuid = :authorUuid",
        countQuery = "SELECT COUNT(p) FROM Post p WHERE p.author.uuid = :authorUuid",
    )
    fun findAllByAuthorUuidWithAuthor(@Param("authorUuid") authorUuid: UUID, pageable: Pageable): Page<Post>

    @Query(
        value = "SELECT p FROM Post p LEFT JOIN FETCH p.author LEFT JOIN FETCH p.party LEFT JOIN FETCH p.plan",
        countQuery = "SELECT COUNT(p) FROM Post p",
    )
    fun findAllWithAuthor(pageable: Pageable): Page<Post>
}
