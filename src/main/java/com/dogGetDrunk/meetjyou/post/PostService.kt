package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PostNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PreferenceNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.post.PostUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.party.dto.CreatePartyRequest
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.dto.CompanionSpec
import com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest
import com.dogGetDrunk.meetjyou.post.dto.CreatePostResponse
import com.dogGetDrunk.meetjyou.post.dto.GetPostResponse
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostRequest
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostResponse
import com.dogGetDrunk.meetjyou.preference.CompPreference
import com.dogGetDrunk.meetjyou.preference.CompPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.toCompanionSpec
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
    private val planRepository: PlanRepository,
) {
    private val log = LoggerFactory.getLogger(PostService::class.java)

    @Transactional
    fun createPost(request: CreatePostRequest): CreatePostResponse {
        val author = userRepository.findByUuid(request.authorUuid)
            ?: throw UserNotFoundException(request.authorUuid)

        if (request.planUuid != null) {
            if (!planRepository.existsByUuid(request.planUuid)) {
                throw PlanNotFoundException(request.planUuid)
            }
            requireNotNull(request.isPlanPublic) { "planUuid가 주어진 경우 isPlanPublic은 null일 수 없습니다." }
        }

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
            if (request.planUuid != null) {
                this.plan = planRepository.findByUuid(request.planUuid)
                this.isPlanPublic = request.isPlanPublic!!
            }
        }

        postRepository.save(newPost)
        if (request.companionSpec != null) {
            saveCompPreference(newPost, request.companionSpec)
        }

        log.info("New post created: $newPost")
        partyService.createParty(CreatePartyRequest.from(newPost))

        return CreatePostResponse.of(newPost, request.companionSpec)
    }

    @Transactional(readOnly = true)
    fun getPostByUuid(postUuid: UUID): GetPostResponse {
        val post = postRepository.findByUuid(postUuid)
            ?: throw PostNotFoundException(postUuid)
        return GetPostResponse.of(post, compPreferenceRepository.findAllByPost(post).toCompanionSpec())
    }

    @Transactional(readOnly = true)
    fun getPostByAuthorUuid(authorUuid: UUID, pageable: Pageable): Page<GetPostResponse> {
        if (!userRepository.existsByUuid(authorUuid)) {
            throw UserNotFoundException(authorUuid)
        }

        return postRepository.findAllByAuthor_Uuid(authorUuid, pageable)
            .map { post ->
                GetPostResponse.of(post, compPreferenceRepository.findAllByPost(post).toCompanionSpec())
            }
    }

    @Transactional(readOnly = true)
    fun verifyPostAuthor(postUuid: UUID, userUuid: UUID): Boolean {
        return postRepository.existsByUuidAndAuthor_Uuid(postUuid, userUuid)
    }

    @Transactional(readOnly = true)
    fun getAllPosts(pageable: Pageable): Page<GetPostResponse> {
        return postRepository.findAll(pageable)
            .map { post ->
                GetPostResponse.of(post, compPreferenceRepository.findAllByPost(post).toCompanionSpec())
            }
    }

    @Transactional
    fun updatePost(postUuid: UUID, request: UpdatePostRequest): UpdatePostResponse {
        val post = postRepository.findByUuid(postUuid)
            ?: throw PostNotFoundException(postUuid)
        val userUuid = SecurityUtil.getCurrentUserUuid()

        if (userUuid != post.author.uuid) { // 이러면 author가 통째로 로드되야 함 -> 성능 이슈
            throw PostUpdateAccessDeniedException(postUuid, post.author.uuid, userUuid)
        }

        if (request.planUuid != null) {
            if (!planRepository.existsByUuid(request.planUuid)) {
                throw PlanNotFoundException(request.planUuid)
            }
            requireNotNull(request.isPlanPublic) { "planUuid가 주어진 경우 isPlanPublic은 null일 수 없습니다." }
        }

        post.apply {
            title = request.title
            content = request.content
            isInstant = request.isInstant
            itinStart = request.itinStart
            itinFinish = request.itinFinish
            location = request.location
            capacity = request.capacity
            isPlanPublic = request.isPlanPublic
        }

        if (request.companionSpec != null) {
            compPreferenceRepository.deleteAllByPost(post) // delete + create가 아닌 update로 바꾸는 게 좋을 듯
            saveCompPreference(post, request.companionSpec)
        }

        log.info("Post updated: ${post.uuid}")
        return UpdatePostResponse.of(post, request.companionSpec)
    }

    @Transactional
    fun deletePost(postUuid: UUID) {
        val post = postRepository.findByUuid(postUuid)
            ?: throw PostNotFoundException(postUuid)
        val userUuid = SecurityUtil.getCurrentUserUuid()

        if (userUuid != post.author.uuid) {
            throw PostUpdateAccessDeniedException(postUuid, post.author.uuid, userUuid)
        }

        compPreferenceRepository.deleteAllByPost(post)
        postRepository.delete(post)

        log.info("Post deleted: uuid=$postUuid")
    }

    private fun saveCompPreference(post: Post, companionSpec: CompanionSpec?) {
        if (companionSpec == null) {
            log.debug("No companion preferences for postUuid: {}", post.uuid)
            return
        }

        val formalizedNames: Map<PreferenceType, List<String>> = companionSpec.formalizeTypesToNames()
        if (formalizedNames.isEmpty()) {
            log.debug("No valid companion preferences for postUuid: {}", post.uuid)
            return
        }

        val compPreferences: List<CompPreference> = buildList {
            formalizedNames.forEach { (type, names) ->
                val preferences = preferenceRepository.findAllByTypeAndNameIn(type, names)
                val foundNames = preferences.map { it.name }.toSet()
                val missing = names.toSet() - foundNames
                if (missing.isNotEmpty()) {
                    log.warn("Unknown preference names for type $type: $missing")
                    throw PreferenceNotFoundException(missing.joinToString(", "))
                }
                addAll(preferences.map { pref -> CompPreference(post, pref) })
            }
        }

        val deduped = compPreferences.distinctBy { it.preference.id }
        compPreferenceRepository.saveAll(deduped)
        log.debug("Saved {} companion preferences for postUuid: {}", deduped.size, post.uuid)
    }
}
