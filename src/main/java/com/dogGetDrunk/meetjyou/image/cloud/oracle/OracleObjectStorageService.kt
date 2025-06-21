package com.dogGetDrunk.meetjyou.image.cloud.oracle

import com.dogGetDrunk.meetjyou.image.cloud.CloudImageService
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.model.CopyObjectDetails
import com.oracle.bmc.objectstorage.requests.CopyObjectRequest
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest
import com.oracle.bmc.objectstorage.requests.GetObjectRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import com.oracle.bmc.objectstorage.responses.CopyObjectResponse
import com.oracle.bmc.objectstorage.responses.GetObjectResponse
import com.oracle.bmc.workrequests.WorkRequestClient
import com.oracle.bmc.workrequests.model.WorkRequest
import com.oracle.bmc.workrequests.requests.GetWorkRequestRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

@Service
class OracleObjectStorageService(
    @Value("\${oracle.oci.namespace}")
    val namespace: String,

    @Value("\${oracle.oci.bucketName}")
    val bucketName: String,

    private val objectStorageClient: ObjectStorageClient,
    private val workRequestClient: WorkRequestClient,
) : CloudImageService {
    // TODO: return try가 아닌 throw로 GlobalExceptionHandler에서 처리하도록 변경
    private val FINAL_FILE_TYPE = "jpg"
    private val THUMBNAIL_WIDTH = 150

    private val log = LoggerFactory.getLogger(OracleObjectStorageService::class.java)

    override fun uploadUserProfileImage(userId: String, file: ByteArray, fileType: String): Boolean {
        val (originalPath, thumbnailPath) = generateUserProfileImagePath(userId)

        val convertedFile = if (fileType.lowercase() in listOf("jpg", "jpeg")) {
            file
        } else {
            convertToJpg(file)
        }

        uploadToObjectStorage(originalPath, convertedFile)
        uploadToObjectStorage(thumbnailPath, createThumbnail(convertedFile))

        return true
    }

    override fun downloadUserProfileImage(userId: String, isThumbnail: Boolean): ByteArray? {
        val (originalPath, thumbnailPath) = generateUserProfileImagePath(userId)
        val objectPath = if (isThumbnail) {
            thumbnailPath
        } else {
            originalPath
        }

        val request = GetObjectRequest.builder()
            .namespaceName(namespace)
            .bucketName(bucketName)
            .objectName(objectPath)
            .build()

        return try {
            val response: GetObjectResponse = objectStorageClient.getObject(request)
            response.inputStream.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    override fun deleteUserProfileImage(userId: String): Boolean {
        val originalPath = "user/${userId}-profile.jpg"
        val thumbnailPath = "user/${userId}-thumbnail.jpg"

        return try {
            val originalRequest = DeleteObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .objectName(originalPath)
                .build()
            objectStorageClient.deleteObject(originalRequest)

            val thumbnailRequest = DeleteObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .objectName(thumbnailPath)
                .build()
            objectStorageClient.deleteObject(thumbnailRequest)

            true
        } catch (e: Exception) {
            println("Error deleting user profile image: ${e.message}")
            false
        }
    }

    override fun uploadPostImage(uuid: UUID, file: MultipartFile): Boolean {
        val fileType = file.originalFilename?.substringAfterLast('.') ?: "jpg"
        val (originalPath, thumbnailPath) = generatePostImagePath(uuid.toString())

        val convertedFile = if (fileType.lowercase() in listOf("jpg", "jpeg")) {
            file.bytes
        } else {
            convertToJpg(file.bytes)
        }

        uploadToObjectStorage(originalPath, convertedFile)
        uploadToObjectStorage(thumbnailPath, createThumbnail(file.bytes))

        return true
    }

    override fun downloadPostImage(uuid: UUID): ByteArray? {
        val (originalPath, thumbnailPath) = generatePostImagePath(uuid.toString())

        val originalImgRequest = GetObjectRequest.builder()
            .namespaceName(namespace)
            .bucketName(bucketName)
            .objectName(originalPath)
            .build()

        return try {
            val response: GetObjectResponse = objectStorageClient.getObject(originalImgRequest)
            response.inputStream.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    override fun deletePostImage(uuid: UUID): Boolean {
        val objectPath = generatePostImagePath(uuid.toString())

        return try {
            val request = DeleteObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .objectName(objectPath.first)
                .build()
            objectStorageClient.deleteObject(request)
            true
        } catch (e: Exception) {
            println("Error deleting post image $objectPath: ${e.message}")
            false
        }
    }

    override fun uploadPartyImage(
        uuid: UUID,
        file: MultipartFile,
    ): Boolean {
        val fileType = file.originalFilename?.substringAfterLast('.') ?: "jpg"
        val (originalPath, thumbnailPath) = generatePartyImagePath(uuid.toString())

        val convertedFile = if (fileType.lowercase() in listOf("jpg", "jpeg")) {
            file.bytes
        } else {
            convertToJpg(file.bytes)
        }

        uploadToObjectStorage(originalPath, convertedFile)
        uploadToObjectStorage(thumbnailPath, createThumbnail(file.bytes))

        return true
    }

    override fun downloadOriginalPartyImage(uuid: UUID): ByteArray? {
        val (originalPath, thumbnailPath) = generatePartyImagePath(uuid.toString())

        val originalImgRequest = GetObjectRequest.builder()
            .namespaceName(namespace)
            .bucketName(bucketName)
            .objectName(originalPath)
            .build()

        return try {
            val response: GetObjectResponse = objectStorageClient.getObject(originalImgRequest)
            response.inputStream.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    override fun downloadThumbnailPartyImage(uuid: UUID): ByteArray? {
        val (_, thumbnailPath) = generatePartyImagePath(uuid.toString())

        val thumbnailImgRequest = GetObjectRequest.builder()
            .namespaceName(namespace)
            .bucketName(bucketName)
            .objectName(thumbnailPath)
            .build()

        return try {
            val response: GetObjectResponse = objectStorageClient.getObject(thumbnailImgRequest)
            response.inputStream.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    override fun deletePartyImage(uuid: UUID): Boolean {
        val (originalPath, thumbnailPath) = generatePartyImagePath(uuid.toString())

        return try {
            val request = DeleteObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .objectName(originalPath)
                .build()
            objectStorageClient.deleteObject(request)
            true
        } catch (e: Exception) {
            println("Error deleting party image $originalPath: ${e.message}")
            false
        }
    }

    override fun setDefaultPartyImage(partyUuid: UUID, postUuid: UUID): Boolean {
        val (originalPostImagePath, thumbnailPostImagePath) = generatePostImagePath(postUuid.toString())
        val (originalPartyImagePath, thumbnailPartyImagePath) = generatePartyImagePath(partyUuid.toString())

        val originalResult = copyImage(originalPostImagePath, originalPartyImagePath)
        val thumbnailResult = copyImage(thumbnailPostImagePath, thumbnailPartyImagePath)
        // 이렇게 하면 최대 20초 (타임아웃 * 2) 기다림. copyImage가 실패하는 순간 더 이상 요청하지 않고 종료시켜야 할듯?

        return originalResult && thumbnailResult
    }

    fun copyImage(sourcePath: String, destinationPath: String): Boolean {
        log.info("Start copying object from $sourcePath to $destinationPath")

        val copyDetails = CopyObjectDetails.builder()
            .sourceObjectName(sourcePath)
            .destinationBucket(bucketName)
            .destinationNamespace(namespace)
            .destinationObjectName(destinationPath)
            .build()

        val request = CopyObjectRequest.builder()
            .namespaceName(namespace)
            .bucketName(bucketName)
            .copyObjectDetails(copyDetails)
            .build()

        return try {
            val response: CopyObjectResponse = objectStorageClient.copyObject(request)
            val workRequestId = response.opcWorkRequestId

            waitForCopyCompletion(workRequestId, 10L, 1000L)
        } catch (e: Exception) {
            log.error("CopyObject failed for $sourcePath → $destinationPath: ${e.message}")
            false
        }
    }

    private fun waitForCopyCompletion(
        workRequestId: String,
        timeoutSeconds: Long = 10L,
        pollIntervalMillis: Long = 1000L,
    ): Boolean {
        val startTime = System.currentTimeMillis()

        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > timeoutSeconds * 1000) {
                log.error("CopyObject timeout ($timeoutSeconds s) exceeded for workRequestId=$workRequestId")
                return false
            }

            val request = GetWorkRequestRequest.builder()
                .workRequestId(workRequestId)
                .build()

            val response = try {
                workRequestClient.getWorkRequest(request)
            } catch (e: Exception) {
                log.error("Error polling work request: ${e.message}")
                return false
            }

            val status = response.workRequest.status
            log.debug("Polling copyObject status: {}", status)

            when (status) {
                WorkRequest.Status.Succeeded -> {
                    log.info("CopyObject succeeded for workRequestId=$workRequestId")
                    return true
                }

                WorkRequest.Status.Failed -> {
                    log.error("CopyObject failed for workRequestId=$workRequestId")
                    return false
                }

                else -> Thread.sleep(pollIntervalMillis)
            }
        }
    }

    private fun uploadToObjectStorage(objectPath: String, file: ByteArray) {
        val request = PutObjectRequest.builder()
            .namespaceName(namespace)
            .bucketName(bucketName)
            .objectName(objectPath)
            .putObjectBody(ByteArrayInputStream(file))
            .contentLength(file.size.toLong())
            .build()

        objectStorageClient.putObject(request)
    }

    private fun convertToJpg(imageBytes: ByteArray): ByteArray {
        val inputStream = ByteArrayInputStream(imageBytes)
        val originalImage = ImageIO.read(inputStream)

        // JPG는 투명도(알파 채널)를 지원하지 않기 때문에 RGB 타입으로 새 이미지 생성
        val jpgImage = BufferedImage(
            originalImage.width,
            originalImage.height,
            BufferedImage.TYPE_INT_RGB
        )

        // 배경을 흰색으로 채우고, PNG를 JPG 이미지로 변환
        val graphics = jpgImage.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, jpgImage.width, jpgImage.height)
        graphics.drawImage(originalImage, 0, 0, null)
        graphics.dispose()

        // 결과를 ByteArray로 출력
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(jpgImage, "jpg", outputStream)
        return outputStream.toByteArray()
    }

    private fun createThumbnail(imageBytes: ByteArray): ByteArray {
        log.info("Creating thumbnail for image of size: ${imageBytes.size} bytes")
        val inputStream = ByteArrayInputStream(imageBytes)
        val originalImage = ImageIO.read(inputStream)

        val height = (originalImage.height * THUMBNAIL_WIDTH) / originalImage.width
        val resizedImage = originalImage.getScaledInstance(THUMBNAIL_WIDTH, height, Image.SCALE_SMOOTH)

        val thumbnail = BufferedImage(THUMBNAIL_WIDTH, height, BufferedImage.TYPE_INT_RGB)
        val graphics = thumbnail.createGraphics()
        graphics.drawImage(resizedImage, 0, 0, null)
        graphics.dispose()

        val outputStream = ByteArrayOutputStream()
        ImageIO.write(thumbnail, "jpg", outputStream)
        return outputStream.toByteArray()
    }

    private fun generateUserProfileImagePath(userId: String): Pair<String, String> {
        val originalPath = "user/$userId-profile.$FINAL_FILE_TYPE"
        val thumbnailPath = "user/$userId-thumbnail.$FINAL_FILE_TYPE"
        return Pair(originalPath, thumbnailPath)
    }

    private fun generatePostImagePath(param: String): Pair<String, String> {
        val originalPath = "post/$param.$FINAL_FILE_TYPE"
        val thumbnailPath = "post/$param-thumb.$FINAL_FILE_TYPE"
        return Pair(originalPath, thumbnailPath)
    }

    private fun generatePartyImagePath(param: String): Pair<String, String> {
        val originalPath = "party/$param.$FINAL_FILE_TYPE"
        val thumbnailPath = "party/$param-thumb.$FINAL_FILE_TYPE"
        return Pair(originalPath, thumbnailPath)
    }
}
