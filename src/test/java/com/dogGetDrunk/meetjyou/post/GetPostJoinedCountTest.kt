package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyKeyService
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.view.PostViewService
import com.dogGetDrunk.meetjyou.preference.CompPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class GetPostJoinedCountTest : BehaviorSpec() {

    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val compPreferenceRepository = mockk<CompPreferenceRepository>(relaxed = true)
    private val preferenceRepository = mockk<PreferenceRepository>(relaxed = true)
    private val partyService = mockk<PartyService>(relaxed = true)
    private val planRepository = mockk<PlanRepository>(relaxed = true)
    private val markerRepository = mockk<MarkerRepository>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val postViewService = mockk<PostViewService>(relaxed = true)
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val chatRoomRepository = mockk<ChatRoomRepository>(relaxed = true)
    private val idempotencyKeyService = mockk<IdempotencyKeyService>(relaxed = true)
    private val sut = PostService(
        postRepository, userRepository, compPreferenceRepository, preferenceRepository,
        partyService, planRepository, markerRepository, userPartyRepository, postViewService,
        currentUserProvider, chatRoomRepository, idempotencyKeyService,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        val user = UserFixtures.user()
        val party = NotificationCenterFixtures.party()
        val post = NotificationCenterFixtures.post(party, user)

        beforeEach {
            clearAllMocks()
            val principal = CustomUserPrincipal(user.uuid, user.email)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(principal, null, emptyList())
            every { currentUserProvider.uuid } returns user.uuid
            every { postRepository.findByUuid(post.uuid) } returns post
            every { compPreferenceRepository.findAllByPost(post) } returns emptyList()
            every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, user.uuid) } returns null
        }

        afterEach { SecurityContextHolder.clearContext() }

        given("파티에 멤버가 가입해 party.joined가 늘어난 뒤 getPostByUuid를 호출하면") {
            `when`("party.joined가 초기값(1)에서 3으로 증가한 상태라면") {
                then("응답의 joined는 party.joined인 3을 반영해야 한다") {
                    party.joined = 3

                    sut.getPostByUuid(post.uuid).joined shouldBe 3
                }
            }
        }
    }
}
