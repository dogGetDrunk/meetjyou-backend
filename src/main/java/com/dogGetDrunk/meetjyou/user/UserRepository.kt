package com.dogGetDrunk.meetjyou.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUuid(uuid: UUID): User?
    fun existsByUuid(uuid: UUID): Boolean
    fun existsByEmail(email: String): Boolean
    fun existsByNickname(nickname: String): Boolean
    fun deleteByUuid(uuid: UUID): Boolean
}
