package com.dogGetDrunk.meetjyou.image.cloud.oracle

import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import com.dogGetDrunk.meetjyou.config.OracleProps
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
    private val props: OracleProps,
    private val objectStorageClient: ObjectStorageClient,
    private val workRequestClient: WorkRequestClient,
) : CloudImageService {
    // TODO: return try가 아닌 throw로 GlobalExceptionHandler에서 처리하도록 변경
    private val FINAL_FILE_TYPE = "jpg"
    private val THUMBNAIL_WIDTH = 150

    private val log = LoggerFactory.getLogger(OracleObjectStorageService::class.java)

    override fun uploadUserProfileImage(file: MultipartFile): Boolean {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        val (originalPath, thumbnailPath) = generateUserProfileImagePath(userUuid.toString())
        val fileType = file.originalFilename?.substringAfterLast('.') ?: "jpg"

        val convertedFile = if (fileType.lowercase() in listOf("jpg", "jpeg")) {
            file.bytes
        } else {
            convertToJpg(file.bytes)
        }

        uploadToObjectStorage(originalPath, convertedFile)
        uploadToObjectStorage(thumbnailPath, createThumbnail(convertedFile))

        return true
    }

    override fun downloadOriginalUserProfileImage(userUuid: UUID): ByteArray? {
        val (originalPath, _) = generateUserProfileImagePath(userUuid.toString())

        return downloadFromObjectStorage(originalPath)
    }

    override fun downloadThumbnailUserProfileImage(userUuid: UUID): ByteArray? {
        val (_, thumbnailPath) = generateUserProfileImagePath(userUuid.toString())

        return downloadFromObjectStorage(thumbnailPath)
    }

    override fun deleteUserProfileImage(): Boolean {
        val userUuid = SecurityUtil.getCurrentUserUuid()
        val (originalPath, thumbnailPath) = generateUserProfileImagePath(userUuid.toString())

        return deleteFromObjectStorage(originalPath) && deleteFromObjectStorage(thumbnailPath)
    }

    override fun uploadPostImage(postUuid: UUID, file: MultipartFile): Boolean {
        val fileType = file.originalFilename?.substringAfterLast('.') ?: "jpg"
        val (originalPath, thumbnailPath) = generatePostImagePath(postUuid.toString())

        val convertedFile = if (fileType.lowercase() in listOf("jpg", "jpeg")) {
            file.bytes
        } else {
            convertToJpg(file.bytes)
        }

        uploadToObjectStorage(originalPath, convertedFile)
        uploadToObjectStorage(thumbnailPath, createThumbnail(file.bytes))

        return true
    }

    override fun downloadOriginalPostImage(postUuid: UUID): ByteArray? {
        val (originalPath, _) = generatePostImagePath(postUuid.toString())

        return downloadFromObjectStorage(originalPath)
    }

    override fun downloadThumbnailPostImage(postUuid: UUID): ByteArray? {
        val (_, thumbnailPath) = generatePostImagePath(postUuid.toString())

        return downloadFromObjectStorage(thumbnailPath)
    }

    override fun deletePostImage(postUuid: UUID): Boolean {
        val (originalPath, thumbnailPath) = generatePostImagePath(postUuid.toString())

        return deleteFromObjectStorage(originalPath) && deleteFromObjectStorage(thumbnailPath)
    }

    override fun uploadPartyImage(partyUuid: UUID, file: MultipartFile): Boolean {
        val fileType = file.originalFilename?.substringAfterLast('.') ?: "jpg"
        val (originalPath, thumbnailPath) = generatePartyImagePath(partyUuid.toString())

        val convertedFile = if (fileType.lowercase() in listOf("jpg", "jpeg")) {
            file.bytes
        } else {
            convertToJpg(file.bytes)
        }

        uploadToObjectStorage(originalPath, convertedFile)
        uploadToObjectStorage(thumbnailPath, createThumbnail(file.bytes))

        return true
    }

    override fun downloadOriginalPartyImage(partyUuid: UUID): ByteArray? {
        val (originalPath, _) = generatePartyImagePath(partyUuid.toString())

        return downloadFromObjectStorage(originalPath)
    }

    override fun downloadThumbnailPartyImage(partyUuid: UUID): ByteArray? {
        val (_, thumbnailPath) = generatePartyImagePath(partyUuid.toString())

        return downloadFromObjectStorage(thumbnailPath)
    }

    override fun deletePartyImage(partyUuid: UUID): Boolean {
        val (originalPath, thumbnailPath) = generatePartyImagePath(partyUuid.toString())

        return deleteFromObjectStorage(originalPath) && deleteFromObjectStorage(thumbnailPath)
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
            .destinationBucket(props.bucketName)
            .destinationNamespace(props.namespace)
            .destinationObjectName(destinationPath)
            .build()

        val request = CopyObjectRequest.builder()
            .namespaceName(props.namespace)
            .bucketName(props.bucketName)
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
            .namespaceName(props.namespace)
            .bucketName(props.bucketName)
            .objectName(objectPath)
            .putObjectBody(ByteArrayInputStream(file))
            .contentLength(file.size.toLong())
            .build()

        objectStorageClient.putObject(request)
    }

    private fun downloadFromObjectStorage(objectPath: String): ByteArray? {
        val getObjectRequest = GetObjectRequest.builder()
            .namespaceName(props.namespace)
            .bucketName(props.bucketName)
            .objectName(objectPath)
            .build()

        return try {
            val response: GetObjectResponse = objectStorageClient.getObject(getObjectRequest)
            response.inputStream.readBytes()
        } catch (e: Exception) {
            log.error("Error downloading image from $objectPath: ${e.message}")
            null
        }
    }

    private fun deleteFromObjectStorage(objectPath: String): Boolean {
        return try {
            val deleteRequest = DeleteObjectRequest.builder()
                .namespaceName(props.namespace)
                .bucketName(props.bucketName)
                .objectName(objectPath)
                .build()
            objectStorageClient.deleteObject(deleteRequest)
            true
        } catch (e: Exception) {
            log.error("Error deleting image from $objectPath: ${e.message}")
            false
        }
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

    private fun generateUserProfileImagePath(param: String): Pair<String, String> {
        val originalPath = "user/$param-profile.$FINAL_FILE_TYPE"
        val thumbnailPath = "user/$param-thumbnail.$FINAL_FILE_TYPE"
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
