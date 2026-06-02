package com.dogGetDrunk.meetjyou.version

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.VersionNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.version.DuplicateVersionException
import com.dogGetDrunk.meetjyou.common.util.SemVer
import com.dogGetDrunk.meetjyou.version.dto.VersionCheckResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AppVersionService(
    private val appVersionRepository: AppVersionRepository,
) {
    private val log = LoggerFactory.getLogger(AppVersionService::class.java)

    fun checkVersion(platform: Platform, clientVersion: String): VersionCheckResponse {
        val versions = appVersionRepository.findAllByPlatform(platform)
        val latest = versions.maxWithOrNull { a, b -> SemVer.compare(a.version, b.version) }
        val minimum = versions
            .filter { it.forceUpdate }
            .maxWithOrNull { a, b -> SemVer.compare(a.version, b.version) }

        val updateRequired = minimum != null && SemVer.compare(clientVersion, minimum.version) < 0
        val updateAvailable = latest != null && SemVer.compare(clientVersion, latest.version) < 0

        return VersionCheckResponse(
            updateRequired = updateRequired,
            updateAvailable = updateAvailable,
            latestVersion = latest?.version,
            downloadUrl = latest?.downloadUrl,
        )
    }

    fun getLatestVersion(platform: Platform): AppVersionDto {
        val versions = appVersionRepository.findAllByPlatform(platform)
        return versions
            .maxWithOrNull { a, b -> SemVer.compare(a.version, b.version) }
            ?.let { AppVersionDto.fromEntity(it) }
            ?: throw VersionNotFoundException("No version registered for platform $platform")
    }

    fun getAllVersions(platform: Platform): List<AppVersionDto> =
        appVersionRepository.findAllByPlatform(platform).map { AppVersionDto.fromEntity(it) }

    fun addVersion(platform: Platform, dto: AppVersionDto) {
        if (appVersionRepository.findByVersionAndPlatform(dto.version, platform) != null) {
            throw DuplicateVersionException("${dto.version} (${platform.name})")
        }
        val appVersion = AppVersion(
            platform = platform,
            version = dto.version,
            forceUpdate = dto.forceUpdate,
            downloadUrl = dto.downloadUrl,
        )
        appVersionRepository.save(appVersion)
        log.info("New version registered: {} ({})", dto.version, platform)
    }

    fun updateVersion(version: String, platform: Platform, dto: AppVersionDto) {
        val appVersion = appVersionRepository.findByVersionAndPlatform(version, platform)
            ?: throw VersionNotFoundException("$version (${platform.name})")

        val prevForceUpdate = appVersion.forceUpdate
        val prevDownloadUrl = appVersion.downloadUrl

        appVersion.forceUpdate = dto.forceUpdate
        appVersion.downloadUrl = dto.downloadUrl
        appVersionRepository.save(appVersion)

        log.info(
            "Version updated: forceUpdate {} -> {}, downloadUrl {} -> {} (version: {}, platform: {})",
            prevForceUpdate, dto.forceUpdate, prevDownloadUrl, dto.downloadUrl, version, platform,
        )
    }

    fun toggleForceUpdate(version: String, platform: Platform): Boolean {
        val appVersion = appVersionRepository.findByVersionAndPlatform(version, platform)
            ?: throw VersionNotFoundException("$version (${platform.name})")

        val original = appVersion.forceUpdate
        appVersion.forceUpdate = !original
        appVersionRepository.save(appVersion)

        log.info("Force update toggled: {} -> {} (version: {}, platform: {})", original, !original, version, platform)
        return !original
    }

    fun updateDownloadUrl(version: String, platform: Platform, newUrl: String) {
        val appVersion = appVersionRepository.findByVersionAndPlatform(version, platform)
            ?: throw VersionNotFoundException("$version (${platform.name})")

        val oldUrl = appVersion.downloadUrl
        appVersion.downloadUrl = newUrl
        appVersionRepository.save(appVersion)

        log.info("Download URL updated: {} -> {} (version: {}, platform: {})", oldUrl, newUrl, version, platform)
    }

    fun deleteVersion(version: String, platform: Platform) {
        val appVersion = appVersionRepository.findByVersionAndPlatform(version, platform)
            ?: throw VersionNotFoundException("$version (${platform.name})")

        appVersionRepository.delete(appVersion)
        log.info("Version deleted: {} ({})", version, platform)
    }
}
