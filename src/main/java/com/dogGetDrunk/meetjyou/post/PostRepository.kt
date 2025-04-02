package com.dogGetDrunk.meetjyou.post

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PostRepository : JpaRepository<Post, Long> {
    fun findAllByAuthor_Id(authorId: Long, pageable: Pageable): Page<Post>
    override fun findAll(pageable: Pageable): Page<Post>
}
