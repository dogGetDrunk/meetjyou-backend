package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserTermsRepository : JpaRepository<UserTerms, Long> {
    fun findAllByUser(user: User): List<UserTerms>

    fun findTopByUser_IdAndTerms_TypeOrderByIdDesc(userId: Long, type: TermsType): UserTerms?

    @Query(
        """
        SELECT ut
        FROM UserTerms ut
        WHERE ut.terms.type = :type
          AND ut.action = :action
          AND ut.id = (
              SELECT MAX(ut2.id)
              FROM UserTerms ut2
              WHERE ut2.user = ut.user
                AND ut2.terms.type = :type
          )
        """,
    )
    fun findLatestByTermsType(type: TermsType, action: TermsAgreementAction): List<UserTerms>
}
