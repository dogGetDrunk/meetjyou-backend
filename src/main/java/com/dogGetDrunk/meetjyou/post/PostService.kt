package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PostNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PreferenceNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.party.dto.CreatePartyRequest
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
    private val partyService: PartyService,
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
            joined = request.joined,
        ).apply {
            this.author = author
        }

        postRepository.save(newPost)
        saveCompPreference(newPost, request)

        log.info("New post created: $newPost")
        partyService.createParty(CreatePartyRequest.from(newPost))

        return CreatePostResponse.of(newPost, compPreferenceRepository.findAllByPost(newPost))
    }

    @Transactional(readOnly = true)
    fun getPostByUuid(postUuid: UUID): GetPostResponse {
        val post = postRepository.findByUuid(postUuid)
            ?: throw PostNotFoundException(postUuid)

        return GetPostResponse.of(post, compPreferenceRepository.findAllByPost(post))
    }

    @Transactional(readOnly = true)
    fun getPostByAuthorUuid(authorUuid: UUID, pageable: Pageable): Page<GetPostResponse> {
        if (!userRepository.existsByUuid(authorUuid)) {
            throw UserNotFoundException(authorUuid)
        }

        return postRepository.findAllByAuthor_Uuid(authorUuid, pageable)
            .map { post ->
                GetPostResponse.of(post, compPreferenceRepository.findAllByPost(post))
            }
    }

    @Transactional(readOnly = true)
    fun getAllPosts(pageable: Pageable): Page<GetPostResponse> {
        return postRepository.findAll(pageable)
            .map { post ->
                GetPostResponse.of(post, compPreferenceRepository.findAllByPost(post))
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
        return UpdatePostResponse.of(post, compPreferenceRepository.findAllByPost(post))
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
        preferences += request.compDiet.map {
            CompPreference(post, findOrThrow(it.name, PreferenceType.DIET))
        }
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
                authorUuid = request.authorUuid,
                isInstant = request.isInstant,
                itinStart = request.itinStart,
                itinFinish = request.itinFinish,
                location = request.location,
                capacity = request.capacity,
                joined = request.joined,
                compGender = request.compGender,
                compAge = request.compAge,
                compPersonalities = request.compPersonalities,
                compTravelStyles = request.compTravelStyles,
                compDiet = request.compDiet,
                compEtc = request.compEtc,
                planUuid = request.planUuid,
            )
        )
    }

    private fun findOrThrow(name: String, type: PreferenceType): Preference {
        return preferenceRepository.findByNameAndType(name, type)
            ?: throw PreferenceNotFoundException("Preference not found: $name of type ${type.name}")
    }
}
