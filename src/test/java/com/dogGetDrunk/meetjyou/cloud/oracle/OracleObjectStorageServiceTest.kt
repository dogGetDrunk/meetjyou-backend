package com.dogGetDrunk.meetjyou.cloud.oracle

import com.dogGetDrunk.meetjyou.config.OracleProps
import com.oracle.bmc.model.BmcException
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest
import com.oracle.bmc.objectstorage.requests.GetObjectRequest
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse
import com.oracle.bmc.objectstorage.responses.GetObjectResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.ByteArrayInputStream

class OracleObjectStorageServiceTest : BehaviorSpec() {
    private val objectStorageClient = mockk<ObjectStorageClient>()
    private val props = OracleProps(
        bucketName = "test-bucket",
        namespace = "test-namespace",
        parExpirationMinutes = 60,
        region = "ap-seoul-1",
    )
    private val sut = OracleObjectStorageService(objectStorageClient, props)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }
        afterSpec { unmockkAll() }

        given("업로드 PAR을 요청하면") {
            `when`("createUploadPar를 호출하면") {
                then("PUT 메서드와 PAR URL을 반환한다") {
                    val par = mockk<PreauthenticatedRequest> {
                        every { accessUri } returns "/p/token/n/test-namespace/b/test-bucket/o/image/user/profile/x-original.jpg"
                    }
                    every { objectStorageClient.createPreauthenticatedRequest(any<CreatePreauthenticatedRequestRequest>()) } returns
                        mockk<CreatePreauthenticatedRequestResponse> { every { preauthenticatedRequest } returns par }

                    val result = sut.createUploadPar("image/user/profile/x-original.jpg")

                    result.httpMethod shouldBe "PUT"
                    result.url shouldBe "https://objectstorage.ap-seoul-1.oraclecloud.com/p/token/n/test-namespace/b/test-bucket/o/image/user/profile/x-original.jpg"
                }
            }
        }

        given("동일한 오브젝트 키로 다운로드 PAR을 반복 요청하면") {
            `when`("createDownloadPar를 두 번 호출하면") {
                then("두 번째 호출은 캐시를 사용하고 OCI를 다시 호출하지 않는다") {
                    val par = mockk<PreauthenticatedRequest> { every { accessUri } returns "/p/token/download" }
                    every { objectStorageClient.createPreauthenticatedRequest(any<CreatePreauthenticatedRequestRequest>()) } returns
                        mockk<CreatePreauthenticatedRequestResponse> { every { preauthenticatedRequest } returns par }

                    val first = sut.createDownloadPar("image/post/x-original.jpg")
                    val second = sut.createDownloadPar("image/post/x-original.jpg")

                    first shouldBe second
                    verify(exactly = 1) { objectStorageClient.createPreauthenticatedRequest(any<CreatePreauthenticatedRequestRequest>()) }
                }
            }
        }

        given("오브젝트를 정상적으로 삭제할 수 있을 때") {
            `when`("deleteObject를 호출하면") {
                then("true를 반환한다") {
                    every { objectStorageClient.deleteObject(any<DeleteObjectRequest>()) } returns mockk(relaxed = true)

                    val result = sut.deleteObject("image/post/x-original.jpg")

                    result shouldBe true
                }
            }
        }

        given("오브젝트 삭제 중 예외가 발생하면") {
            `when`("deleteObject를 호출하면") {
                then("예외를 삼키고 false를 반환한다") {
                    every { objectStorageClient.deleteObject(any<DeleteObjectRequest>()) } throws
                        BmcException(500, "Internal error", "InternalError", "req-id")

                    val result = sut.deleteObject("image/post/x-original.jpg")

                    result shouldBe false
                }
            }
        }

        given("오브젝트가 존재할 때") {
            `when`("getObjectContent를 호출하면") {
                then("바이트 배열을 반환한다") {
                    val content = "hello".toByteArray()
                    every { objectStorageClient.getObject(any<GetObjectRequest>()) } returns
                        mockk<GetObjectResponse> { every { inputStream } returns ByteArrayInputStream(content) }

                    val result = sut.getObjectContent("image/post/x-original.jpg")

                    result shouldBe content
                }
            }
        }

        given("오브젝트가 존재하지 않을 때 (404)") {
            `when`("getObjectContent를 호출하면") {
                then("null을 반환한다") {
                    every { objectStorageClient.getObject(any<GetObjectRequest>()) } throws
                        BmcException(404, "Not found", "ObjectNotFound", "req-id")

                    val result = sut.getObjectContent("image/post/missing.jpg")

                    result shouldBe null
                }
            }
        }

        given("오브젝트 조회 중 404가 아닌 오류가 발생하면") {
            `when`("getObjectContent를 호출하면") {
                then("예외를 그대로 전파한다") {
                    every { objectStorageClient.getObject(any<GetObjectRequest>()) } throws
                        BmcException(500, "Internal error", "InternalError", "req-id")

                    shouldThrow<BmcException> {
                        sut.getObjectContent("image/post/x-original.jpg")
                    }
                }
            }
        }
    }
}
