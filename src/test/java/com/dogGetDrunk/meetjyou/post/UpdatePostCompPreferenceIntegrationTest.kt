package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.place.support.PlaceTestDoubles
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.dto.CompanionSpec
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostRequest
import com.dogGetDrunk.meetjyou.preference.CompPreference
import com.dogGetDrunk.meetjyou.preference.CompPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.Preference
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * The comp_preference unique index lives on the entity too, so H2 enforces it: replacing the
 * preferences while keeping one used to insert it before the old row's DELETE ran.
 */
@SpringBootTest
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class, AuthenticationDetailsProvider::class, ObjectStorageClient::class, WorkRequestClient::class,
)
class UpdatePostCompPreferenceIntegrationTest : BehaviorSpec() {
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var parties: PartyRepository
    @Autowired private lateinit var posts: PostRepository
    @Autowired private lateinit var plans: PlanRepository
    @Autowired private lateinit var markers: MarkerRepository
    @Autowired private lateinit var rooms: ChatRoomRepository
    @Autowired private lateinit var memberships: UserPartyRepository
    @Autowired private lateinit var compPreferences: CompPreferenceRepository
    @Autowired private lateinit var preferences: PreferenceRepository
    @Autowired private lateinit var manager: PlatformTransactionManager

    private val provider = mockk<CurrentUserProvider>()
    private val tx by lazy { TransactionTemplate(manager) }
    private val partyService by lazy {
        PartyService(parties, posts, plans, markers, rooms, mockk(relaxed = true), mockk(relaxed = true),
            memberships, users, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), ObjectMapper(),
            provider, PlaceTestDoubles.regionCatalog())
    }
    private val service by lazy {
        PostService(posts, users, compPreferences, preferences, partyService, plans, markers, memberships,
            mockk(relaxed = true), provider, rooms, mockk(relaxed = true),
                PlaceTestDoubles.resolver(), PlaceTestDoubles.regionCatalog())
    }

    private class Seed(val host: UUID, val post: UUID, val kept: Preference, val added: Preference)

    private fun seed(): Seed = tx.execute {
        val tag = UUID.randomUUID().toString().take(TAG_LENGTH)
        val host = users.save(
            User(email = "$tag@example.com", nickname = "h$tag", authProvider = AuthProvider.KAKAO, externalId = tag),
        )
        val party = parties.save(Party(itinStart = START, itinFinish = FINISH, destination = LOCATION,
            joined = 1, capacity = CAPACITY, name = "Party"))
        memberships.save(UserParty(party, host, PartyRole.HOST))
        val post = Post(party, false, "Title", "content", START, FINISH, LOCATION, CAPACITY)
        post.author = host
        posts.save(post)
        val kept = preferences.save(Preference(PreferenceType.GENDER, UUID.randomUUID().toString()))
        val added = preferences.save(Preference(PreferenceType.GENDER, UUID.randomUUID().toString()))
        compPreferences.save(CompPreference(post, kept))
        Seed(host.uuid, post.uuid, kept, added)
    } ?: error("seed failed")

    private fun requestKeeping(seed: Seed): UpdatePostRequest {
        val companion = mockk<CompanionSpec>()
        every { companion.formalizeTypesToNames() } returns
            mapOf(PreferenceType.GENDER to listOf(seed.kept.name, seed.added.name))
        return UpdatePostRequest("Title", "content", false, START, FINISH, LOCATION, CAPACITY, companion, null, null)
    }

    init {
        extensions(SpringExtension())

        given("동행 조건에 선호 항목 하나가 저장된 게시글") {
            `when`("그 항목을 유지한 채 다른 항목을 추가해 수정하면") {
                then("AT-4-1 유니크 제약 위반 없이 새 조건으로 교체된다") {
                    val seed = seed()
                    every { provider.uuid } returns seed.host

                    shouldNotThrowAny {
                        tx.executeWithoutResult { service.updatePost(seed.post, requestKeeping(seed)) }
                    }

                    tx.executeWithoutResult {
                        val post = posts.findByUuid(seed.post) ?: error("post missing")
                        compPreferences.findAllByPost(post).map { it.preference.id } shouldContainExactlyInAnyOrder
                            listOf(seed.kept.id, seed.added.id)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG_LENGTH = 8
        private const val CAPACITY = 4
        private const val LOCATION = "Busan"
        private val START = Instant.parse("2030-05-01T00:00:00Z")
        private val FINISH = Instant.parse("2030-05-03T00:00:00Z")
    }
}
