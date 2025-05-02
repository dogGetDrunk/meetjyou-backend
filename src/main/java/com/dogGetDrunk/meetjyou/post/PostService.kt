package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PostNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PreferenceNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
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
import java.util.UUID

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
        val author = userRepository.findByUuid(request.authorUuid)
            ?: throw UserNotFoundException(request.authorUuid)

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
            uuid = newPost.uuid.toString(),
            title = newPost.title,
            content = newPost.content,
            postStatus = newPost.postStatus,
            createdAt = newPost.createdAt,
            lastEditedAt = newPost.lastEditedAt,
            authorUuid = newPost.author.uuid.toString(),
            isInstant = newPost.isInstant,
            itinStart = newPost.itinStart,
            itinFinish = newPost.itinFinish,
            location = newPost.location,
            capacity = newPost.capacity,
            planId = newPost.plan?.id,
            compGender = request.compGender.name,
            compAge = request.compAge.name,
            compPersonalities = request.compPersonalities.map { it.name },
            compTravelStyles = request.compTravelStyles.map { it.name },
            compDiet = request.compDiet.name,
            compEtc = request.compEtc.map { it.name },
        )
    }

    @Transactional(readOnly = true)
    fun getPostByUuid(postUuid: UUID): GetPostResponse {
        val post = postRepository.findByUuid(postUuid)
            ?: throw PostNotFoundException(postUuid)

        val compPreferences = compPreferenceRepository.findAllByPost(post)
        val compGender = compPreferences.find { it.preference.type == 0 }?.preference?.name
        val compAge = compPreferences.find { it.preference.type == 1 }?.preference?.name
        val compPersonalities = compPreferences.filter { it.preference.type == 2 }.map { it.preference.name }
        val compTravelStyles = compPreferences.filter { it.preference.type == 3 }.map { it.preference.name }
        val compDiet = compPreferences.filter { it.preference.type == 4 }.map { it.preference.name }
        val compEtc = compPreferences.filter { it.preference.type == 5 }.map { it.preference.name }

        return GetPostResponse(
            uuid = post.uuid.toString(),
            title = post.title,
            content = post.content,
            postStatus = post.postStatus,
            views = post.views,
            createdAt = post.createdAt,
            lastEditedAt = post.lastEditedAt,
            authorUuid = post.author.uuid.toString(),
            isInstant = post.isInstant,
            itinStart = post.itinStart,
            itinFinish = post.itinFinish,
            location = post.location,
            capacity = post.capacity,
            planId = post.plan?.id,
            compGender = compGender,
            compAge = compAge,
            compPersonalities = compPersonalities,
            compTravelStyles = compTravelStyles,
            compDiet = compDiet,
            compEtc = compEtc,
        )
    }

    @Transactional(readOnly = true)
    fun getPostByAuthorUuid(authorUuid: UUID, pageable: Pageable): Page<GetPostResponse> {
        if (!userRepository.existsByUuid(authorUuid)) {
            throw UserNotFoundException(authorUuid)
        }

        return postRepository.findAllByAuthor_Uuid(authorUuid, pageable)
            .map { post ->
                val compPreferences = compPreferenceRepository.findAllByPost(post)
                val compGender = compPreferences.find { it.preference.type == 0 }?.preference?.name
                val compAge = compPreferences.find { it.preference.type == 1 }?.preference?.name
                val compPersonalities = compPreferences.filter { it.preference.type == 2 }.map { it.preference.name }
                val compTravelStyles = compPreferences.filter { it.preference.type == 3 }.map { it.preference.name }
                val compDiet = compPreferences.filter { it.preference.type == 4 }.map { it.preference.name }
                val compEtc = compPreferences.filter { it.preference.type == 5 }.map { it.preference.name }

                GetPostResponse(
                    uuid = post.uuid.toString(),
                    title = post.title,
                    content = post.content,
                    postStatus = post.postStatus,
                    views = post.views,
                    createdAt = post.createdAt,
                    lastEditedAt = post.lastEditedAt,
                    authorUuid = post.author.uuid.toString(),
                    isInstant = post.isInstant,
                    itinStart = post.itinStart,
                    itinFinish = post.itinFinish,
                    location = post.location,
                    capacity = post.capacity,
                    planId = post.plan?.id,
                    compGender = compGender,
                    compAge = compAge,
                    compPersonalities = compPersonalities,
                    compTravelStyles = compTravelStyles,
                    compDiet = compDiet,
                    compEtc = compEtc,
                )
            }
    }

    @Transactional(readOnly = true)
    fun getAllPosts(pageable: Pageable): Page<GetPostResponse> {
        return postRepository.findAll(pageable)
            .map { post ->
                val compPreferences = compPreferenceRepository.findAllByPost(post)
                val compGender = compPreferences.find { it.preference.type == 0 }?.preference?.name
                val compAge = compPreferences.find { it.preference.type == 1 }?.preference?.name
                val compPersonalities = compPreferences.filter { it.preference.type == 2 }.map { it.preference.name }
                val compTravelStyles = compPreferences.filter { it.preference.type == 3 }.map { it.preference.name }
                val compDiet = compPreferences.filter { it.preference.type == 4 }.map { it.preference.name }
                val compEtc = compPreferences.filter { it.preference.type == 5 }.map { it.preference.name }

                GetPostResponse(
                    uuid = post.uuid.toString(),
                    title = post.title,
                    content = post.content,
                    postStatus = post.postStatus,
                    views = post.views,
                    createdAt = post.createdAt,
                    lastEditedAt = post.lastEditedAt,
                    authorUuid = post.author.uuid.toString(),
                    isInstant = post.isInstant,
                    itinStart = post.itinStart,
                    itinFinish = post.itinFinish,
                    location = post.location,
                    capacity = post.capacity,
                    planId = post.plan?.id,
                    compGender = compGender,
                    compAge = compAge,
                    compPersonalities = compPersonalities,
                    compTravelStyles = compTravelStyles,
                    compDiet = compDiet,
                    compEtc = compEtc,
                )
            }
    }

    @Transactional
    fun updatePost(postUuid: UUID, request: UpdatePostRequest): UpdatePostResponse {
        val post = postRepository.findByUuid(postUuid)
            ?: throw PostNotFoundException(postUuid)

        post.apply {
            title = request.title
            content = request.content
            isInstant = request.isInstant
            itinStart = request.itinStart
            itinFinish = request.itinFinish
            location = request.location
            capacity = request.capacity
        }

        compPreferenceRepository.deleteAllByPost(post)
        saveCompPreference(post, request)

        log.info("Post updated: $post")

        return UpdatePostResponse(
            uuid = post.uuid.toString(),
            title = post.title,
            content = post.content,
            postStatus = post.postStatus,
            createdAt = post.createdAt,
            lastEditedAt = post.lastEditedAt,
            authorUuid = post.author.uuid.toString(),
            isInstant = post.isInstant,
            itinStart = post.itinStart,
            itinFinish = post.itinFinish,
            location = post.location,
            capacity = post.capacity,
            planUuid = post.plan?.uuid.toString(),
            compGender = request.compGender,
            compAge = request.compAge,
            compPersonalities = request.compPersonalities,
            compTravelStyles = request.compTravelStyles,
            compDiet = request.compDiet,
            compEtc = request.compEtc,
        )
    }

    @Transactional
    fun deletePost(postUuid: UUID) {
        val post = postRepository.findByUuid(postUuid)
            ?: throw PostNotFoundException(postUuid)

        compPreferenceRepository.deleteAllByPost(post)
        postRepository.delete(post)

        log.info("Post deleted: uuid=$postUuid")
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
                authorUuidString = request.authorUuid.toString(),
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
