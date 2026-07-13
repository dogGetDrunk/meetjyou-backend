package com.dogGetDrunk.meetjyou.post

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PostRepository : JpaRepository<Post, Long> {
    fun findByUuid(uuid: UUID): Post?
    fun findByParty_Uuid(partyUuid: UUID): Post?
    fun findAllByParty_UuidIn(partyUuids: Collection<UUID>): List<Post>
    fun findAllByAuthor_Uuid(authorUuid: UUID, pageable: Pageable): Page<Post>
    override fun findAll(pageable: Pageable): Page<Post>
    fun existsByUuidAndAuthor_Uuid(uuid: UUID, authorUuid: UUID): Boolean
    fun existsByPlan_UuidAndIsPlanPublicTrue(planUuid: UUID): Boolean
    fun findAllByPlan_UuidIn(planUuids: Collection<UUID>): List<Post>

    @Query(
        value = "SELECT p FROM Post p LEFT JOIN FETCH p.author WHERE p.author.uuid = :authorUuid",
        countQuery = "SELECT COUNT(p) FROM Post p WHERE p.author.uuid = :authorUuid",
    )
    fun findAllByAuthorUuidWithAuthor(@Param("authorUuid") authorUuid: UUID, pageable: Pageable): Page<Post>

    @Query(
        value = "SELECT p FROM Post p LEFT JOIN FETCH p.author",
        countQuery = "SELECT COUNT(p) FROM Post p",
    )
    fun findAllWithAuthor(pageable: Pageable): Page<Post>
}
