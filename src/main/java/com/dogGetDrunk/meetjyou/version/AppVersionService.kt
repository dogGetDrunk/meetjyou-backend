package com.dogGetDrunk.meetjyou.version

import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.VersionNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.version.DuplicateVersionException
import com.dogGetDrunk.meetjyou.common.util.SemVer
import com.dogGetDrunk.meetjyou.version.dto.VersionCheckResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class AppVersionService(
    private val appVersionRepository: AppVersionRepository,
    private val platformStoreUrlRepository: PlatformStoreUrlRepository,
) {
    private val log = LoggerFactory.getLogger(AppVersionService::class.java)

    // Populated on demand from the DB and invalidated on every write below.
    // /check is hit on every app launch, so avoid re-scanning all versions each time.
    private val summaryCache = ConcurrentHashMap<Platform, VersionSummary>()

    fun checkVersion(platform: Platform, clientVersion: String): VersionCheckResponse {
        val summary = summaryCache.getOrPut(platform) { buildSummary(platform) }
        val downloadUrl = platformStoreUrlRepository.findById(platform).orElse(null)?.downloadUrl

        if (!SemVer.isValid(clientVersion)) {
            log.warn("Malformed clientVersion '{}' for platform {}; skipping update check", clientVersion, platform)
            return VersionCheckResponse(
                updateRequired = false,
                updateAvailable = false,
                latestVersion = summary.latestVersion,
                downloadUrl = downloadUrl,
                message = null,
            )
        }

        val updateRequired = summary.minimumVersion != null && SemVer.compare(clientVersion, summary.minimumVersion) < 0
        val updateAvailable = summary.latestVersion != null && SemVer.compare(clientVersion, summary.latestVersion) < 0
        val message = when {
            updateRequired -> summary.minimumMessage
            updateAvailable -> summary.latestMessage
            else -> null
        }

        return VersionCheckResponse(
            updateRequired = updateRequired,
            updateAvailable = updateAvailable,
            latestVersion = summary.latestVersion,
            downloadUrl = downloadUrl,
            message = message,
        )
    }

    fun getLatestVersion(platform: Platform): AppVersionDto {
        val versions = appVersionRepository.findAllByPlatform(platform).filter { it.storeReleased }
        return versions
            .maxWithOrNull { a, b -> SemVer.compare(a.version, b.version) }
            ?.let { AppVersionDto.fromEntity(it) }
            ?: throw VersionNotFoundException("No released version registered for platform $platform")
    }

    fun getAllVersions(platform: Platform): List<AppVersionDto> =
        appVersionRepository.findAllByPlatform(platform).map { AppVersionDto.fromEntity(it) }

    fun addVersion(platform: Platform, dto: AppVersionDto) {
        validateVersionFormat(dto.version)
        if (appVersionRepository.findByVersionAndPlatform(dto.version, platform) != null) {
            throw DuplicateVersionException("${dto.version} (${platform.name})")
        }
        // storeReleased always starts false, regardless of the request body: a new version must
        // never affect /check until an admin confirms it is actually live in the store.
        val appVersion = AppVersion(
            platform = platform,
            version = dto.version,
            forceUpdate = dto.forceUpdate,
            message = dto.message,
        )
        appVersionRepository.save(appVersion)
        summaryCache.remove(platform)
        log.info("New version registered (unreleased): {} ({})", dto.version, platform)
    }

    fun updateVersion(version: String, platform: Platform, dto: AppVersionDto) {
        val appVersion = appVersionRepository.findByVersionAndPlatform(version, platform)
            ?: throw VersionNotFoundException("$version (${platform.name})")

        val prevForceUpdate = appVersion.forceUpdate
        val prevMessage = appVersion.message

        appVersion.forceUpdate = dto.forceUpdate
        appVersion.message = dto.message
        appVersionRepository.save(appVersion)
        summaryCache.remove(platform)

        log.info(
            "Version updated: forceUpdate {} -> {}, message {} -> {} (version: {}, platform: {})",
            prevForceUpdate, dto.forceUpdate, prevMessage, dto.message, version, platform,
        )
    }

    fun toggleForceUpdate(version: String, platform: Platform): Boolean {
        val appVersion = appVersionRepository.findByVersionAndPlatform(version, platform)
            ?: throw VersionNotFoundException("$version (${platform.name})")

        val original = appVersion.forceUpdate
        appVersion.forceUpdate = !original
        appVersionRepository.save(appVersion)
        summaryCache.remove(platform)

        log.info("Force update toggled: {} -> {} (version: {}, platform: {})", original, !original, version, platform)
        return !original
    }

    fun toggleStoreReleased(version: String, platform: Platform): Boolean {
        val appVersion = appVersionRepository.findByVersionAndPlatform(version, platform)
            ?: throw VersionNotFoundException("$version (${platform.name})")

        val original = appVersion.storeReleased
        appVersion.storeReleased = !original
        appVersionRepository.save(appVersion)
        summaryCache.remove(platform)

        log.info("Store release toggled: {} -> {} (version: {}, platform: {})", original, !original, version, platform)
        return !original
    }

    fun updateStoreUrl(platform: Platform, newUrl: String) {
        val oldUrl = platformStoreUrlRepository.findById(platform).orElse(null)?.downloadUrl
        platformStoreUrlRepository.save(PlatformStoreUrl(platform, newUrl))
        log.info("Store URL updated: {} -> {} (platform: {})", oldUrl, newUrl, platform)
    }

    fun deleteVersion(version: String, platform: Platform) {
        val appVersion = appVersionRepository.findByVersionAndPlatform(version, platform)
            ?: throw VersionNotFoundException("$version (${platform.name})")

        appVersionRepository.delete(appVersion)
        summaryCache.remove(platform)
        log.info("Version deleted: {} ({})", version, platform)
    }

    private fun validateVersionFormat(version: String) {
        if (!SemVer.isValid(version)) {
            throw InvalidInputException(value = version, message = "Version must be in numeric x.y.z format")
        }
    }

    private fun buildSummary(platform: Platform): VersionSummary {
        val versions = appVersionRepository.findAllByPlatform(platform).filter { it.storeReleased }
        val latest = versions.maxWithOrNull { a, b -> SemVer.compare(a.version, b.version) }
        val minimum = versions.filter { it.forceUpdate }.maxWithOrNull { a, b -> SemVer.compare(a.version, b.version) }
        return VersionSummary(
            latestVersion = latest?.version,
            latestMessage = latest?.message,
            minimumVersion = minimum?.version,
            minimumMessage = minimum?.message,
        )
    }

    private data class VersionSummary(
        val latestVersion: String?,
        val latestMessage: String?,
        val minimumVersion: String?,
        val minimumMessage: String?,
    )
}
