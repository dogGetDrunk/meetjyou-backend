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
    val bucketName: String
) : CloudImageService {
    private val homeDir = System.getProperty("user.home") // 홈 디렉토리 가져오기
    private val configPath = Paths.get(homeDir, ".oci", "config").toString()
    private val provider = ConfigFileAuthenticationDetailsProvider(configPath, "DEFAULT")
    private val objectStorageClient: ObjectStorageClient = ObjectStorageClient.builder().build(provider)

    override fun uploadImage(userId: String, file: ByteArray, fileType: String): String {
        val finalFileType = "jpg"
        val originalPath = "$userId/profile.$finalFileType"
        val thumbnailPath = "$userId/thumbnail.$finalFileType"

        // 기존 이미지 삭제
        deleteImage(userId)

        // JPG가 아닌 경우 변환
        val convertedFile = if (fileType.lowercase() in listOf("jpg", "jpeg")) {
            file
        } else {
            convertToJpg(file)
        }

        // 원본 이미지 업로드
        uploadToObjectStorage(originalPath, convertedFile)

        // 썸네일 생성 및 업로드
        val thumbnail = createThumbnail(convertedFile)
        uploadToObjectStorage(thumbnailPath, thumbnail)

        return "https://objectstorage.${provider.region.regionId}.oraclecloud.com" +
                "/n/$namespace/b/$bucketName/o/$originalPath"
    }

    override fun downloadImage(userId: String, isThumbnail: Boolean): ByteArray? {
        val fileName = if (isThumbnail) {
            "thumbnail.jpg"
        } else {
            "profile.jpg"
        }
        val objectPath = "$userId/$fileName"

        val request = GetObjectRequest.builder()
            .namespaceName(namespace)
            .bucketName(bucketName)
            .objectName(objectPath)
            .build()

        val response: GetObjectResponse = objectStorageClient.getObject(request)
        return response.inputStream.readBytes()
    }

    override fun deleteImage(userId: String): Boolean {
        val originalPath = "$userId/profile.jpg"
        val thumbnailPath = "$userId/thumbnail.jpg"

        listOf(originalPath, thumbnailPath).forEach { path ->
            try {
                val request = DeleteObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .objectName(path)
                    .build()
                objectStorageClient.deleteObject(request)
            } catch (e: Exception) {
                println("Error deleting $path: ${e.message}")
            }
        }
        return true
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

    /**
     * JPG가 아닌 경우 JPG로 변환하는 함수
     */
    private fun convertToJpg(imageBytes: ByteArray): ByteArray {
        val inputStream = ByteArrayInputStream(imageBytes)
        val originalImage = ImageIO.read(inputStream)

        val outputStream = ByteArrayOutputStream()
        ImageIO.write(originalImage, "jpg", outputStream)
        return outputStream.toByteArray()
    }

    /**
     * 썸네일 생성 함수 (150px 너비로 리사이징)
     */
    private fun createThumbnail(imageBytes: ByteArray): ByteArray {
        val inputStream = ByteArrayInputStream(imageBytes)
        val originalImage = ImageIO.read(inputStream)

        val width = 150
        val height = (originalImage.height * width) / originalImage.width
        val resizedImage = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH)

        val thumbnail = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = thumbnail.createGraphics()
        graphics.drawImage(resizedImage, 0, 0, null)
        graphics.dispose()

        val outputStream = ByteArrayOutputStream()
        ImageIO.write(thumbnail, "jpg", outputStream)
        return outputStream.toByteArray()
    }
}
