package com.dogGetDrunk.meetjyou.cloud.oracle

import com.dogGetDrunk.meetjyou.config.OracleProps
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class OracleObjectStorageServiceCacheBenchmarkTest : DescribeSpec({

    val simulatedLatencyMs = 200L

    val props = OracleProps(
        bucketName = "test-bucket",
        namespace = "test-namespace",
        parExpirationMinutes = 30,
        region = "ap-chuncheon-1",
    )

    fun mockOciClient(): ObjectStorageClient {
        val client = mockk<ObjectStorageClient>()
        every { client.createPreauthenticatedRequest(any<CreatePreauthenticatedRequestRequest>()) } answers {
            Thread.sleep(simulatedLatencyMs)
            val par = PreauthenticatedRequest.builder()
                .id("par-id")
                .accessUri("/p/test-access-uri/${UUID.randomUUID()}")
                .build()
            CreatePreauthenticatedRequestResponse.builder()
                .preauthenticatedRequest(par)
                .build()
        }
        every { client.deleteObject(any<DeleteObjectRequest>()) } returns
            DeleteObjectResponse.builder().build()
        return client
    }

    describe("Download PAR caching benchmark") {

        it("cache MISS - 10 calls each hitting OCI API: ~2000ms expected") {
            val client = mockOciClient()
            val service = OracleObjectStorageService(client, props)
            val iterations = 10

            // Use different keys each time to force cache misses
            val keys = List(iterations) { "image/post/${UUID.randomUUID()}-thumbnail.jpg" }

            val start = System.nanoTime()
            keys.forEach { service.createDownloadPar(it) }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000

            println()
            println("=== Cache MISS ($iterations calls, each hitting OCI API) ===")
            println("Total: ${elapsedMs}ms | Avg: ${elapsedMs / iterations}ms per call")

            verify(exactly = iterations) {
                client.createPreauthenticatedRequest(any<CreatePreauthenticatedRequestRequest>())
            }
        }

        it("cache HIT - 10 calls, only 1st hits OCI API: ~200ms expected") {
            val client = mockOciClient()
            val service = OracleObjectStorageService(client, props)
            val objectKey = "image/post/${UUID.randomUUID()}-thumbnail.jpg"
            val iterations = 10

            val start = System.nanoTime()
            repeat(iterations) { service.createDownloadPar(objectKey) }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000

            println()
            println("=== Cache HIT ($iterations calls, only 1st hits OCI API) ===")
            println("Total: ${elapsedMs}ms | Avg: ${elapsedMs / iterations}ms per call")

            verify(exactly = 1) {
                client.createPreauthenticatedRequest(any<CreatePreauthenticatedRequestRequest>())
            }
        }

        it("realistic scenario - 20 thumbnails x 5 page loads") {
            val postKeys = List(10) { "image/post/${UUID.randomUUID()}-thumbnail.jpg" }
            val userKeys = List(10) { "image/user/profile/${UUID.randomUUID()}-thumbnail.jpg" }
            val allKeys = postKeys + userKeys

            // --- Simulate WITHOUT cache: new service per "page load" ---
            val noCacheClient = mockOciClient()
            var noCacheTotalMs = 0L
            repeat(5) {
                val service = OracleObjectStorageService(noCacheClient, props)
                val start = System.nanoTime()
                allKeys.forEach { key -> service.createDownloadPar(key) }
                noCacheTotalMs += (System.nanoTime() - start) / 1_000_000
            }

            // --- WITH cache: single service instance across page loads ---
            val cacheClient = mockOciClient()
            val cachedService = OracleObjectStorageService(cacheClient, props)
            val cacheStart = System.nanoTime()
            repeat(5) {
                allKeys.forEach { key -> cachedService.createDownloadPar(key) }
            }
            val cacheTotalMs = (System.nanoTime() - cacheStart) / 1_000_000

            val speedup = noCacheTotalMs.toDouble() / maxOf(cacheTotalMs, 1).toDouble()

            println()
            println("=== Realistic scenario: 20 thumbnails x 5 page loads ===")
            println("Without cache: ${noCacheTotalMs}ms (100 OCI API calls)")
            println("With cache:    ${cacheTotalMs}ms (20 OCI API calls + 80 cache hits)")
            println("Speedup:       %.1fx faster".format(speedup))

            // Without cache: 5 loads × 20 keys = 100 OCI calls
            verify(exactly = 100) {
                noCacheClient.createPreauthenticatedRequest(any<CreatePreauthenticatedRequestRequest>())
            }
            // With cache: only 20 unique keys = 20 OCI calls
            verify(exactly = 20) {
                cacheClient.createPreauthenticatedRequest(any<CreatePreauthenticatedRequestRequest>())
            }

            cacheTotalMs shouldBeLessThan noCacheTotalMs
        }

        it("cache returns same PAR URL for same key") {
            val client = mockOciClient()
            val service = OracleObjectStorageService(client, props)
            val objectKey = "image/post/${UUID.randomUUID()}-thumbnail.jpg"

            val first = service.createDownloadPar(objectKey)
            val second = service.createDownloadPar(objectKey)

            first.url shouldBe second.url
            first.expiresAt shouldBe second.expiresAt
        }

        it("cache is invalidated on delete") {
            val client = mockOciClient()
            val service = OracleObjectStorageService(client, props)
            val objectKey = "image/post/${UUID.randomUUID()}-thumbnail.jpg"

            service.createDownloadPar(objectKey)
            service.deleteObject(objectKey)
            service.createDownloadPar(objectKey)

            // Should have hit OCI twice (before and after invalidation)
            verify(exactly = 2) {
                client.createPreauthenticatedRequest(any<CreatePreauthenticatedRequestRequest>())
            }
        }
    }
})
