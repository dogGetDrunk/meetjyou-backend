package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserTermsRepository : JpaRepository<UserTerms, Long> {
    fun findAllByUser(user: User): List<UserTerms>
}
