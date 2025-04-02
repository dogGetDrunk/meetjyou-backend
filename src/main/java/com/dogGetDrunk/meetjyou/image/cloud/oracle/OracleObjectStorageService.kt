package com.dogGetDrunk.meetjyou.image.cloud.oracle

import com.dogGetDrunk.meetjyou.image.cloud.CloudImageService
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest
import com.oracle.bmc.objectstorage.requests.GetObjectRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import com.oracle.bmc.objectstorage.responses.GetObjectResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import javax.imageio.ImageIO

@Service
class OracleObjectStorageService(
    @Value("\${oracle.oci.namespace}")
    val namespace: String,

    @Value("\${oracle.oci.bucketName}")
    val bucketName: String,
) : CloudImageService {

    private val homeDir = System.getProperty("user.home")
    private val configPath = Paths.get(homeDir, ".oci", "config").toString()
    private val provider = ConfigFileAuthenticationDetailsProvider(configPath, "DEFAULT")
    private val objectStorageClient: ObjectStorageClient = ObjectStorageClient.builder().build(provider)
    private val FINAL_FILE_TYPE = "jpg"
    private val THUMBNAIL_WIDTH = 150

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

    override fun uploadPostImage(postId: Long, file: ByteArray, fileType: String): Boolean {
        val objectPath = generatePostImagePath(postId)

        val convertedFile = if (fileType.lowercase() in listOf("jpg", "jpeg")) {
            file
        } else {
            convertToJpg(file)
        }

        uploadToObjectStorage(objectPath, convertedFile)

        return true
    }

    override fun downloadPostImage(postId: Long): ByteArray? {
        val objectPath = generatePostImagePath(postId)

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

    override fun deletePostImage(postId: Long): Boolean {
        val objectPath = "post/$postId.jpg"

        return try {
            val request = DeleteObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .objectName(objectPath)
                .build()
            objectStorageClient.deleteObject(request)
            true
        } catch (e: Exception) {
            println("Error deleting post image $objectPath: ${e.message}")
            false
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

        val outputStream = ByteArrayOutputStream()
        ImageIO.write(originalImage, "jpg", outputStream)
        return outputStream.toByteArray()
    }

    private fun createThumbnail(imageBytes: ByteArray): ByteArray {
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
        val originalPath = "user/${userId}-profile.$FINAL_FILE_TYPE"
        val thumbnailPath = "user/${userId}-thumbnail.$FINAL_FILE_TYPE"
        return Pair(originalPath, thumbnailPath)
    }

    private fun generatePostImagePath(postId: Long): String {
        return "post/$postId.$FINAL_FILE_TYPE"
    }
}
