package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyNotFoundException
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions

/**
 * AT-005: which object's image a party shows, and that the DB reads finish in a read-only transaction
 * before any OCI call (moved from PartyImageAndPlanTest and PartyQueryOptimizationTest).
 */
class PartyImageDownloadServiceTest : BehaviorSpec() {
    private val partyRepository = mockk<PartyRepository>()
    private val postRepository = mockk<PostRepository>()
    private val partyImgService = mockk<PartyImgService>()
    private val postImgService = mockk<PostImgService>()
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)
    private val sut = PartyImageDownloadService(
        partyRepository, postRepository, partyImgService, postImgService, transactionManager,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun party(state: PartyImageState): Party {
        val party = NotificationCenterFixtures.party()
        party.imageState = state
        return party
    }

    private fun postOf(party: Party) = NotificationCenterFixtures.post(party, UserFixtures.user())

    private fun par(name: String) = ParResponse(url = "https://par/$name", httpMethod = "GET", expiresAt = Instant.EPOCH)

    init {
        given("원본 다운로드") {
            `when`("CUSTOM이면") {
                then("트랜잭션을 끝낸 뒤 파티 PAR을 발급한다") {
                    val party = party(PartyImageState.CUSTOM)
                    every { partyRepository.findByUuid(party.uuid) } returns party
                    every { partyImgService.createPartyOriginalImgDownloadPars(party.uuid) } returns par("party")

                    sut.resolveOriginal(party.uuid) shouldBe par("party")
                    verifyOrder {
                        transactionManager.getTransaction(match { it.isReadOnly })
                        transactionManager.commit(any())
                        partyImgService.createPartyOriginalImgDownloadPars(party.uuid)
                    }
                }
            }

            `when`("INHERITED이고 모집글이 있으면") {
                then("모집글 PAR을 발급한다") {
                    val party = party(PartyImageState.INHERITED)
                    val post = postOf(party)
                    every { partyRepository.findByUuid(party.uuid) } returns party
                    every { postRepository.findByParty_Uuid(party.uuid) } returns post
                    every { postImgService.createPostOriginalImgDownloadPars(post.uuid) } returns par("post")

                    sut.resolveOriginal(party.uuid) shouldBe par("post")
                }
            }

            `when`("INHERITED인데 연결 모집글이 없으면") {
                then("null이고 OCI를 부르지 않는다") {
                    val party = party(PartyImageState.INHERITED)
                    every { partyRepository.findByUuid(party.uuid) } returns party
                    every { postRepository.findByParty_Uuid(party.uuid) } returns null

                    sut.resolveOriginal(party.uuid) shouldBe null
                    verify(exactly = 0) { postImgService.createPostOriginalImgDownloadPars(any()) }
                    verify(exactly = 0) { partyImgService.createPartyOriginalImgDownloadPars(any()) }
                }
            }

            `when`("NONE이면") {
                then("null이고 OCI를 부르지 않는다") {
                    val party = party(PartyImageState.NONE)
                    every { partyRepository.findByUuid(party.uuid) } returns party

                    sut.resolveOriginal(party.uuid) shouldBe null
                    verify(exactly = 0) { partyImgService.createPartyOriginalImgDownloadPars(any()) }
                }
            }

            `when`("파티가 없으면") {
                then("PartyNotFoundException") {
                    val uuid = UUID.randomUUID()
                    every { partyRepository.findByUuid(uuid) } returns null

                    shouldThrow<PartyNotFoundException> { sut.resolveOriginal(uuid) }
                }
            }
        }

        given("썸네일 일괄 다운로드") {
            `when`("CUSTOM, 없는 파티, NONE, 모집글 있는 INHERITED, 모집글 없는 INHERITED를 섞으면") {
                then("입력 순서와 길이를 지키고, 쿼리 2번 뒤 이미지가 있는 것만 OCI를 부른다") {
                    val custom = party(PartyImageState.CUSTOM)
                    val none = party(PartyImageState.NONE)
                    val inherited = party(PartyImageState.INHERITED)
                    val orphan = party(PartyImageState.INHERITED)
                    val post = postOf(inherited)
                    val missing = UUID.randomUUID()
                    val uuids = listOf(custom.uuid, missing, none.uuid, inherited.uuid, orphan.uuid)
                    every { partyRepository.findAllByUuidIn(uuids) } returns listOf(custom, none, inherited, orphan)
                    every { postRepository.findAllByParty_UuidIn(uuids) } returns listOf(post)
                    every { partyImgService.createPartyThumbnailImgDownloadPars(listOf(custom.uuid)) } returns listOf(par("p"))
                    every { postImgService.createPostThumbnailImgDownloadPars(listOf(post.uuid)) } returns listOf(par("q"))

                    sut.resolveThumbnails(uuids) shouldContainExactly listOf(par("p"), null, null, par("q"), null)
                    verify(exactly = 1) { partyRepository.findAllByUuidIn(uuids) }
                    verify(exactly = 1) { postRepository.findAllByParty_UuidIn(uuids) }
                    verify(exactly = 1) { partyImgService.createPartyThumbnailImgDownloadPars(any()) }
                    verify(exactly = 1) { postImgService.createPostThumbnailImgDownloadPars(any()) }
                    verifyOrder {
                        transactionManager.commit(any())
                        partyImgService.createPartyThumbnailImgDownloadPars(listOf(custom.uuid))
                    }
                }
            }
        }

        given("트랜잭션 경계") {
            then("클래스와 공개 메서드에 @Transactional이 없다 (OCI 호출 중 커넥션 점유 방지)") {
                PartyImageDownloadService::class.findAnnotation<Transactional>() shouldBe null
                listOf("resolveOriginal", "resolveThumbnails").forEach { name ->
                    PartyImageDownloadService::class.functions.single { it.name == name }
                        .findAnnotation<Transactional>() shouldBe null
                }
            }
        }
    }
}
