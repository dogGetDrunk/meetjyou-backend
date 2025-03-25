package com.dogGetDrunk.meetjyou.post

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PostRepository : JpaRepository<Post, Long> {
    fun findAllByAuthor_Id(authorId: Long): List<Post>
}
