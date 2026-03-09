package com.dogGetDrunk.meetjyou.terms

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface TermsRepository : JpaRepository<Terms, Long> {
    @Query(
        """
        SELECT t
        FROM Terms t
        WHERE t.status = :status
          AND t.effectiveAt <= :now
        ORDER BY t.required DESC, t.id ASC
        """,
    )
    fun findActiveTerms(
        status: TermsStatus,
        now: Instant,
    ): List<Terms>

    @Query(
        """
        SELECT t
        FROM Terms t
        WHERE t.status = :status
          AND t.required = true
          AND t.effectiveAt <= :now
        """,
    )
    fun findRequiredActiveTerms(
        status: TermsStatus,
        now: Instant,
    ): List<Terms>

    fun findAllByUuidIn(uuids: Collection<UUID>): List<Terms>

    fun findByUuid(uuid: UUID): Terms?
}
