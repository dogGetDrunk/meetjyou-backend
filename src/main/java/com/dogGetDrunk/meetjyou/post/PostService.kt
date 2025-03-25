package com.dogGetDrunk.meetjyou.post

import org.springframework.stereotype.Service

@Service
class PostService(
    private val postRepository: PostRepository
) {
    fun getPostsByAuthorId(authorId: Long): List<PostResponse> {
        return postRepository.findAllByAuthor_Id(authorId)
            .map { post ->
                PostResponse(
                    createdAt = post.createdAt,
                    lastEditedAt = post.lastEditedAt,
                    postStatus = post.postStatus,
                    title = post.title,
                    body = post.body,
                    views = post.views
                )
            }
    }
}
