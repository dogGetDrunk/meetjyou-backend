package com.dogGetDrunk.meetjyou.image.cloud.oracle.service

import com.dogGetDrunk.meetjyou.cloud.oracle.OracleObjectStorageService
import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import java.time.Instant
import java.util.UUID

class PartyImgServiceTest : BehaviorSpec() {
    private val oracleObjectStorageService = mockk<OracleObjectStorageService>()
    private val sut = PartyImgService(oracleObjectStorageService)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }
        afterSpec { unmockkAll() }

        given("파티 이미지 업로드 PAR을 요청하면") {
            `when`("createPartyImgUploadPars를 호출하면") {
                then("원본과 썸네일 오브젝트 키로 각각 PAR을 발급한다") {
                    val uuid = UUID.randomUUID()
                    val keySlot = slot<String>()
                    every { oracleObjectStorageService.createUploadPar(capture(keySlot)) } answers {
                        ParResponse(url = "https://example.com/${keySlot.captured}", httpMethod = "PUT", expiresAt = Instant.now())
                    }

                    val result = sut.createPartyImgUploadPars(uuid)

                    result.map { it.url } shouldBe listOf(
                        "https://example.com/image/party/${uuid}-original.jpg",
                        "https://example.com/image/party/${uuid}-thumbnail.jpg",
                    )
                }
            }
        }

        given("파티 원본 이미지 다운로드 PAR을 요청하면") {
            `when`("createPartyOriginalImgDownloadPars를 호출하면") {
                then("원본 오브젝트 키로 다운로드 PAR을 발급한다") {
                    val uuid = UUID.randomUUID()
                    val keySlot = slot<String>()
                    every { oracleObjectStorageService.createDownloadPar(capture(keySlot)) } answers {
                        ParResponse(url = "https://example.com/${keySlot.captured}", httpMethod = "GET", expiresAt = Instant.now())
                    }

                    val result = sut.createPartyOriginalImgDownloadPars(uuid)

                    result.url shouldBe "https://example.com/image/party/${uuid}-original.jpg"
                }
            }
        }

        given("여러 파티의 썸네일 이미지 다운로드 PAR을 요청하면") {
            `when`("createPartyThumbnailImgDownloadPars를 호출하면") {
                then("각 파티의 썸네일 오브젝트 키로 PAR을 발급한다") {
                    val uuids = listOf(UUID.randomUUID(), UUID.randomUUID())
                    val keySlot = slot<String>()
                    every { oracleObjectStorageService.createDownloadPar(capture(keySlot)) } answers {
                        ParResponse(url = "https://example.com/${keySlot.captured}", httpMethod = "GET", expiresAt = Instant.now())
                    }

                    val result = sut.createPartyThumbnailImgDownloadPars(uuids)

                    result.map { it.url } shouldBe uuids.map { "https://example.com/image/party/${it}-thumbnail.jpg" }
                }
            }
        }

        given("파티 이미지를 삭제하면") {
            `when`("deletePartyImg를 호출하면") {
                then("원본과 썸네일 오브젝트를 모두 삭제한다") {
                    val uuid = UUID.randomUUID()
                    every { oracleObjectStorageService.deleteObject(any()) } returns true

                    sut.deletePartyImg(uuid)

                    verify(exactly = 1) { oracleObjectStorageService.deleteObject("image/party/${uuid}-original.jpg") }
                    verify(exactly = 1) { oracleObjectStorageService.deleteObject("image/party/${uuid}-thumbnail.jpg") }
                }
            }
        }
    }
}
