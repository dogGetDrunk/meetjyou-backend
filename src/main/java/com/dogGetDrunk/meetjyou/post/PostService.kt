package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PostNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PreferenceNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.post.PostUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.chat.room.dto.ChatRoomResponse
import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.party.dto.CreatePartyRequest
import com.dogGetDrunk.meetjyou.plan.Marker
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.Plan
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.plan.dto.GetPlanResponse
import com.dogGetDrunk.meetjyou.post.dto.CompanionSpec
import com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest
import com.dogGetDrunk.meetjyou.post.dto.CreatePostResponse
import com.dogGetDrunk.meetjyou.post.dto.GetPostResponse
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostRequest
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostResponse
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostStatusRequest
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostStatusResponse
import com.dogGetDrunk.meetjyou.post.view.PostViewService
import com.dogGetDrunk.meetjyou.preference.CompPreference
import com.dogGetDrunk.meetjyou.preference.CompPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.toCompanionSpec
import com.dogGetDrunk.meetjyou.party.PartyProgressStatus
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
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
    private val markerRepository: MarkerRepository,
    private val userPartyRepository: UserPartyRepository,
    private val postViewService: PostViewService,
    private val currentUserProvider: CurrentUserProvider,
) {
    private val log = LoggerFactory.getLogger(PostService::class.java)

    @Transactional
    fun createPost(request: CreatePostRequest): CreatePostResponse {
        val author = requireCurrentUser()
        val planRef = resolvePlanReference(request.planUuid, request.isPlanPublic)
        val partyResult = partyService.createParty(buildCreatePartyRequest(request, author.uuid))
        val post = buildPost(author, partyResult.party, request, planRef)
        postRepository.save(post)
        if (request.companionSpec != null) saveCompPreference(post, request.companionSpec)
        log.info("New post created: $post")
        return CreatePostResponse.of(post, request.companionSpec, ChatRoomResponse.of(partyResult.chatRoom))
    }

    @Transactional(readOnly = true)
    fun getPostByUuid(postUuid: UUID): GetPostResponse {
        val post = postRepository.findByUuid(postUuid) ?: throw PostNotFoundException(postUuid)
        val views = postViewService.getViewCount(post.id)
        val response = buildGetPostResponse(post, views)

        val currentUser = userRepository.findByUuid(currentUserProvider.uuid)
        if (currentUser != null) {
            runCatching { postViewService.incrementIfEligible(post.id, currentUser.id) }
                .onFailure { log.warn("Failed to increment view count. postId={}", post.id, it) }
        }

        return response
    }

    @Transactional(readOnly = true)
    fun getPostByAuthorUuid(authorUuid: UUID, pageable: Pageable): Page<GetPostResponse> {
        if (!userRepository.existsByUuid(authorUuid)) throw UserNotFoundException(authorUuid)

        val posts = postRepository.findAllByAuthor_Uuid(authorUuid, pageable)
        if (posts.content.isEmpty()) return posts.map { buildGetPostResponse(it, 0L) }

        return posts.map { buildGetPostResponse(it, loadPostContextMaps(posts.content, currentUserProvider.uuid)) }
    }

    @Transactional(readOnly = true)
    fun verifyPostAuthor(postUuid: UUID, userUuid: UUID): Boolean {
        return postRepository.existsByUuidAndAuthor_Uuid(postUuid, userUuid)
    }

    @Transactional(readOnly = true)
    fun getAllPosts(pageable: Pageable): Page<GetPostResponse> {
        val posts = postRepository.findAll(pageable)
        if (posts.content.isEmpty()) return posts.map { buildGetPostResponse(it, 0L) }

        return posts.map { buildGetPostResponse(it, loadPostContextMaps(posts.content, currentUserProvider.uuid)) }
    }

    @Transactional
    fun updatePost(postUuid: UUID, request: UpdatePostRequest): UpdatePostResponse {
        val post = postRepository.findByUuid(postUuid) ?: throw PostNotFoundException(postUuid)
        val userUuid = currentUserProvider.uuid

        if (userUuid != post.author.uuid) {
            // comparing UUIDs forces full author entity load — potential N+1 on list operations
            throw PostUpdateAccessDeniedException(postUuid, post.author.uuid, userUuid)
        }

        validatePostWritable(postUuid, post)
        applyPlanChange(post, request.planUuid, request.isPlanPublic)

        post.apply {
            title = request.title
            content = request.content
            isInstant = request.isInstant
            itinStart = request.itinStart
            itinFinish = request.itinFinish
            location = request.location
            capacity = request.capacity
        }

        if (request.companionSpec != null) {
            // delete-then-insert instead of update — consider optimizing to an upsert
            compPreferenceRepository.deleteAllByPost(post)
            saveCompPreference(post, request.companionSpec)
        }

        log.info("Post updated: ${post.uuid}")
        return UpdatePostResponse.of(post, request.companionSpec)
    }

    @Transactional
    fun updatePostStatus(postUuid: UUID, request: UpdatePostStatusRequest): UpdatePostStatusResponse {
        val post = postRepository.findByUuid(postUuid) ?: throw PostNotFoundException(postUuid)
        val userUuid = currentUserProvider.uuid

        if (userUuid != post.author.uuid) {
            throw PostUpdateAccessDeniedException(postUuid, post.author.uuid, userUuid)
        }

        post.status = request.status
        log.info("Post status updated: uuid={} status={}", postUuid, request.status)
        return UpdatePostStatusResponse.of(post)
    }

    @Transactional
    fun deletePost(postUuid: UUID) {
        val post = postRepository.findByUuid(postUuid) ?: throw PostNotFoundException(postUuid)
        val userUuid = currentUserProvider.uuid

        if (userUuid != post.author.uuid) {
            throw PostUpdateAccessDeniedException(postUuid, post.author.uuid, userUuid)
        }

        validatePostWritable(postUuid, post)
        compPreferenceRepository.deleteAllByPost(post)
        postRepository.delete(post)
        log.info("Post deleted: uuid=$postUuid")
    }

    private fun requireCurrentUser(): User {
        val uuid = currentUserProvider.uuid
        return userRepository.findByUuid(uuid) ?: throw UserNotFoundException(uuid)
    }

    private data class PostContextMaps(
        val compPrefsMap: Map<Long, List<CompPreference>>,
        val markersMap: Map<UUID, List<Marker>>,
        val myStatusMap: Map<UUID, UserParty>,
        val viewCountMap: Map<Long, Long>,
    )

    private fun loadPostContextMaps(posts: List<Post>, currentUserUuid: UUID): PostContextMaps {
        val planUuids = posts.mapNotNull { if (it.isPlanPublic == true) it.plan?.uuid else null }
        val partyUuids = posts.map { it.party.uuid }
        return PostContextMaps(
            compPrefsMap = compPreferenceRepository.findAllByPostIn(posts).groupBy { it.post.id },
            markersMap = if (planUuids.isEmpty()) emptyMap()
                else markerRepository.findAllByPlan_UuidIn(planUuids).groupBy { it.plan.uuid },
            myStatusMap = userPartyRepository.findAllByParty_UuidInAndUser_Uuid(partyUuids, currentUserUuid)
                .associateBy { it.party.uuid },
            viewCountMap = postViewService.getViewCounts(posts.map { it.id }),
        )
    }

    private fun buildGetPostResponse(post: Post, views: Long): GetPostResponse {
        val companionSpec = compPreferenceRepository.findAllByPost(post).toCompanionSpec()
        val plan = resolvePlanResponse(post.plan?.uuid?.takeIf { post.isPlanPublic == true })
        val myApplicationStatus = userPartyRepository
            .findByParty_UuidAndUser_Uuid(post.party.uuid, currentUserProvider.uuid)
            ?.memberStatus
        return GetPostResponse.of(post, companionSpec, views, plan, myApplicationStatus)
    }

    private fun buildGetPostResponse(post: Post, ctx: PostContextMaps): GetPostResponse {
        val companionSpec = (ctx.compPrefsMap[post.id] ?: emptyList()).toCompanionSpec()
        val plan = resolvePlanResponse(post.plan?.uuid?.takeIf { post.isPlanPublic == true }, ctx.markersMap)
        val myApplicationStatus = ctx.myStatusMap[post.party.uuid]?.memberStatus
        val views = ctx.viewCountMap[post.id] ?: 0L
        return GetPostResponse.of(post, companionSpec, views, plan, myApplicationStatus)
    }

    private fun resolvePlanResponse(planUuid: UUID?): GetPlanResponse? {
        if (planUuid == null) return null
        val markers = markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(planUuid)
        val plan = planRepository.findByUuid(planUuid) ?: return null
        return GetPlanResponse.of(plan, markers)
    }

    private fun resolvePlanResponse(planUuid: UUID?, markersMap: Map<UUID, List<Marker>>): GetPlanResponse? {
        if (planUuid == null) return null
        val plan = planRepository.findByUuid(planUuid) ?: return null
        return GetPlanResponse.of(plan, markersMap[planUuid] ?: emptyList())
    }

    private fun resolvePlanReference(planUuid: UUID?, isPlanPublic: Boolean?): Pair<Plan, Boolean>? {
        if (planUuid == null) return null
        val plan = planRepository.findByUuid(planUuid) ?: throw PlanNotFoundException(planUuid)
        val public = isPlanPublic
            ?: throw InvalidInputException(value = "isPlanPublic", message = "isPlanPublic is required when planUuid is provided")
        return Pair(plan, public)
    }

    private fun applyPlanChange(post: Post, planUuid: UUID?, isPlanPublic: Boolean?) {
        val planRef = resolvePlanReference(planUuid, isPlanPublic)
        post.plan = planRef?.first
        post.isPlanPublic = planRef?.second
        post.party.plan = planRef?.first
        log.info("Post plan synced with party. postUuid=${post.uuid}, planUuid=${planRef?.first?.uuid}")
    }

    private fun validatePostWritable(postUuid: UUID, post: Post) {
        if (post.party?.progressStatus == PartyProgressStatus.COMPLETED) {
            throw InvalidInputException(
                value = postUuid.toString(),
                message = "Post linked to a completed party is read-only.",
            )
        }
    }

    private fun buildCreatePartyRequest(request: CreatePostRequest, ownerUuid: UUID) = CreatePartyRequest(
        itinStart = request.itinStart,
        itinFinish = request.itinFinish,
        destination = request.location,
        capacity = request.capacity,
        joined = 1,
        name = request.title,
        planUuid = request.planUuid,
        ownerUuid = ownerUuid,
    )

    private fun buildPost(author: User, party: Party, request: CreatePostRequest, planRef: Pair<Plan, Boolean>?): Post =
        Post(
            party = party,
            isInstant = request.isInstant,
            title = request.title,
            content = request.content,
            itinStart = request.itinStart,
            itinFinish = request.itinFinish,
            location = request.location,
            capacity = request.capacity,
        ).apply {
            this.author = author
            if (planRef != null) {
                this.plan = planRef.first
                this.isPlanPublic = planRef.second
            }
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
