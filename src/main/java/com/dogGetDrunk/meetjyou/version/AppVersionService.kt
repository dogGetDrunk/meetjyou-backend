package com.dogGetDrunk.meetjyou.version

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.VersionNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AppVersionService(
    private val appVersionRepository: AppVersionRepository,
) {
    private val log = LoggerFactory.getLogger(AppVersionService::class.java)

    fun getLatestVersion(): AppVersionDto =
        appVersionRepository.findFirstByOrderByReleasedAtDesc()?.let { AppVersionDto.fromEntity(it) }
            ?: throw VersionNotFoundException("최신 버전 정보가 없습니다.")

    fun isForceUpdate(currentVersion: String): Boolean =
        appVersionRepository.findByVersion(currentVersion)?.forceUpdate
            ?: throw VersionNotFoundException("버전 정보가 없습니다.")


    fun getAllVersions(): List<AppVersionDto> =
        appVersionRepository.findAll()
            .filterNotNull()
            .map { AppVersionDto.fromEntity(it) }

    fun addVersion(appVersionDto: AppVersionDto) {
        val appVersion = AppVersion(
            version = appVersionDto.version,
            forceUpdate = appVersionDto.forceUpdate,
            downloadUrl = appVersionDto.downloadUrl
        )
        appVersionRepository.save(appVersion)
        log.info("새 버전 등록: {}", appVersion)
    }

    fun updateVersion(version: String, appVersionDto: AppVersionDto) {
        val appVersion = appVersionRepository.findByVersion(version)
            ?: throw VersionNotFoundException(version)

        val prevForceUpdateSetting = appVersion.forceUpdate
        val prevDownloadUrlSetting = appVersion.downloadUrl

        appVersion.forceUpdate = appVersionDto.forceUpdate
        appVersion.downloadUrl = appVersionDto.downloadUrl
        appVersionRepository.save(appVersion)

        log.info(
            "버전 정보 수정: 강제 업데이트 설정: {} -> {}, 다운로드 url: {} -> {}",
            prevForceUpdateSetting, appVersionDto.forceUpdate,
            prevDownloadUrlSetting, appVersionDto.downloadUrl
        )
    }

    fun toggleForceUpdate(version: String): Boolean {
        val appVersion = appVersionRepository.findByVersion(version)
            ?: throw VersionNotFoundException(version)

        val originalForceUpdate = appVersion.forceUpdate
        appVersion.forceUpdate = !originalForceUpdate
        appVersionRepository.save(appVersion)

        log.info("강제 업데이트 여부 변경: {} -> {} (버전: {})", originalForceUpdate, !originalForceUpdate, version)
        return !originalForceUpdate
    }

    fun updateDownloadUrl(version: String, newUrl: String) {
        val appVersion = appVersionRepository.findByVersion(version)
            ?: throw VersionNotFoundException(version)

        val oldUrl = appVersion.downloadUrl
        appVersion.downloadUrl = newUrl
        appVersionRepository.save(appVersion)

        log.info("다운로드 url 변경: {} -> {} (버전: {})", oldUrl, newUrl, version)
    }

    fun deleteVersion(version: String) {
        val appVersion = appVersionRepository.findByVersion(version)
            ?: throw VersionNotFoundException(version)

        appVersionRepository.delete(appVersion)
        log.info("버전 정보 삭제: {}", version)
    }
}
