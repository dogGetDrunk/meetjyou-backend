package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.common.exception.business.PostNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.PreferenceNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.UserNotFoundException
import com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest
import com.dogGetDrunk.meetjyou.post.dto.CreatePostResponse
import com.dogGetDrunk.meetjyou.post.dto.GetPostResponse
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostRequest
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostResponse
import com.dogGetDrunk.meetjyou.preference.CompPreference
import com.dogGetDrunk.meetjyou.preference.CompPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.Preference
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PostService(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val compPreferenceRepository: CompPreferenceRepository,
    private val preferenceRepository: PreferenceRepository,
) {
    private val log = LoggerFactory.getLogger(PostService::class.java)

    @Transactional
    fun createPost(request: CreatePostRequest): CreatePostResponse {
        val author = userRepository.findById(request.authorId)
            .orElseThrow { UserNotFoundException(request.authorId) }

        val newPost = Post(
            title = request.title,
            content = request.content,
            isInstant = request.isInstant,
            itinStart = request.itinStart,
            itinFinish = request.itinFinish,
            location = request.location,
            capacity = request.capacity,
        ).apply {
            this.author = author
        }

        postRepository.save(newPost)
        saveCompPreference(newPost, request)

        log.info("New post created: $newPost")

        return CreatePostResponse(
            title = newPost.title,
            content = newPost.content,
            postStatus = newPost.postStatus,
            createdAt = newPost.createdAt,
            lastEditedAt = newPost.lastEditedAt,
            authorId = newPost.author.id,
            isInstant = newPost.isInstant,
            itinStart = newPost.itinStart,
            itinFinish = newPost.itinFinish,
            location = newPost.location,
            capacity = newPost.capacity,
            planId = newPost.plan?.id,
            compGender = request.compGender,
            compAge = request.compAge,
            compPersonalities = request.compPersonalities,
            compTravelStyles = request.compTravelStyles,
            compDiet = request.compDiet,
            compEtc = request.compEtc,
        )
    }

    @Transactional(readOnly = true)
    fun getPostById(postId: Long): GetPostResponse {
        val post = postRepository.findById(postId)
            .orElseThrow { PostNotFoundException(postId) }

        return GetPostResponse(
            createdAt = post.createdAt,
            lastEditedAt = post.lastEditedAt,
            postStatus = post.postStatus,
            title = post.title,
            content = post.content,
            views = post.views,
            authorId = post.author.id,
        )
    }

    @Transactional(readOnly = true)
    fun getPostsByAuthorId(authorId: Long, pageable: Pageable): Page<GetPostResponse> {
        userRepository.findById(authorId)
            .orElseThrow { UserNotFoundException(authorId) }

        return postRepository.findAllByAuthor_Id(authorId, pageable)
            .map {
                GetPostResponse(
                    createdAt = it.createdAt,
                    lastEditedAt = it.lastEditedAt,
                    postStatus = it.postStatus,
                    title = it.title,
                    content = it.content,
                    views = it.views,
                    authorId = it.author.id,
                )
            }
    }

    @Transactional(readOnly = true)
    fun getAllPosts(pageable: Pageable): Page<GetPostResponse> {
        return postRepository.findAll(pageable)
            .map {
                GetPostResponse(
                    createdAt = it.createdAt,
                    lastEditedAt = it.lastEditedAt,
                    postStatus = it.postStatus,
                    title = it.title,
                    content = it.content,
                    views = it.views,
                    authorId = it.author.id,
                )
            }
    }

    @Transactional
    fun updatePost(postId: Long, request: UpdatePostRequest): UpdatePostResponse {
        val post = postRepository.findById(postId)
            .orElseThrow { PostNotFoundException(postId) }

        post.title = request.title
        post.content = request.content
        post.isInstant = request.isInstant
        post.itinStart = request.itinStart
        post.itinFinish = request.itinFinish
        post.location = request.location
        post.capacity = request.capacity

        compPreferenceRepository.deleteAllByPost(post)
        saveCompPreference(post, request)

        log.info("Post updated: $post")

        return UpdatePostResponse(
            title = post.title,
            content = post.content,
            postStatus = post.postStatus,
            createdAt = post.createdAt,
            lastEditedAt = post.lastEditedAt,
            authorId = post.author.id,
            isInstant = post.isInstant,
            itinStart = post.itinStart,
            itinFinish = post.itinFinish,
            location = post.location,
            capacity = post.capacity,
            planId = post.plan?.id,
            compGender = request.compGender,
            compAge = request.compAge,
            compPersonalities = request.compPersonalities,
            compTravelStyles = request.compTravelStyles,
            compDiet = request.compDiet,
            compEtc = request.compEtc,
        )
    }

    @Transactional
    fun deletePost(postId: Long) {
        val post = postRepository.findById(postId)
            .orElseThrow { PostNotFoundException(postId) }

        compPreferenceRepository.deleteAllByPost(post)
        postRepository.delete(post)

        log.info("Post deleted: id=$postId")
    }

    private fun saveCompPreference(post: Post, request: CreatePostRequest) {
        val preferences = mutableListOf<CompPreference>()

        preferences += CompPreference(post, findOrThrow(request.compGender.name, PreferenceType.GENDER))
        preferences += CompPreference(post, findOrThrow(request.compAge.name, PreferenceType.AGE))
        preferences += request.compPersonalities.map {
            CompPreference(post, findOrThrow(it.name, PreferenceType.PERSONALITY))
        }
        preferences += request.compTravelStyles.map {
            CompPreference(post, findOrThrow(it.name, PreferenceType.TRAVEL_STYLE))
        }
        preferences += CompPreference(post, findOrThrow(request.compDiet.name, PreferenceType.DIET))
        preferences += request.compEtc.map {
            CompPreference(post, findOrThrow(it.name, PreferenceType.ETC))
        }

        compPreferenceRepository.saveAll(preferences)
    }

    private fun saveCompPreference(post: Post, request: UpdatePostRequest) {
        // 오버로드: Update 요청도 동일 처리
        saveCompPreference(
            post,
            CreatePostRequest(
                title = request.title,
                content = request.content,
                authorId = request.authorId,
                isInstant = request.isInstant,
                itinStart = request.itinStart,
                itinFinish = request.itinFinish,
                location = request.location,
                capacity = request.capacity,
                compGender = request.compGender,
                compAge = request.compAge,
                compPersonalities = request.compPersonalities,
                compTravelStyles = request.compTravelStyles,
                compDiet = request.compDiet,
                compEtc = request.compEtc,
                planId = request.planId
            )
        )
    }

    private fun findOrThrow(name: String, type: PreferenceType): Preference {
        return preferenceRepository.findByNameAndType(name, type.type)
            ?: throw PreferenceNotFoundException("Preference not found: $name of type ${type.name}")
    }
}
