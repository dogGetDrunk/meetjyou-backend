package com.dogGetDrunk.meetjyou.version;

import com.dogGetDrunk.meetjyou.common.exception.business.VersionNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppVersionService {

    private final AppVersionRepository appVersionRepository;


    public AppVersionDto getLatestVersion() {
        return appVersionRepository.findFirstByOrderByReleaseDateDesc()
                .map(AppVersionDto::fromEntity)
                .orElseThrow(() -> new VersionNotFoundException("latest"));
    }

    public boolean isForceUpdate(String currentVersion) {
        return appVersionRepository.findByVersion(currentVersion)
                .map(appVersion -> !appVersion.isForceUpdate())
                .orElse(false);
    }

    public List<AppVersionDto> getAllVersions() {
        return appVersionRepository.findAll().stream()
                .map(AppVersionDto::fromEntity)
                .toList();
    }

    public void addVersion(AppVersionDto appVersionDto) {
        AppVersion appVersion = AppVersion.builder()
                .version(appVersionDto.getVersion())
                .forceUpdate(appVersionDto.isForceUpdate())
                .downloadUrl(appVersionDto.getDownloadUrl())
                .build();
        appVersionRepository.save(appVersion);

        log.info("새 버전 등록: {}", appVersion);
    }

    public void updateVersion(String version, AppVersionDto appVersionDto) {
        AppVersion appVersion = appVersionRepository.findByVersion(version)
                .orElseThrow(() -> new VersionNotFoundException(version));

        Boolean prevForceUpdateSetting = appVersion.isForceUpdate();
        String prevDownloadUrlSetting = appVersion.getDownloadUrl();

        appVersion.setForceUpdate(appVersionDto.isForceUpdate());
        appVersion.setDownloadUrl(appVersionDto.getDownloadUrl());
        appVersionRepository.save(appVersion);

        log.info("버전 정보 수정: 강제 업데이트 설정: {} -> {}, 다운로드 url: {} -> {}",
                prevForceUpdateSetting, appVersionDto.isForceUpdate(),
                prevDownloadUrlSetting, appVersionDto.getDownloadUrl());
    }


    public boolean toggleForceUpdate(String version) {
        AppVersion appVersion = appVersionRepository.findByVersion(version)
                .orElseThrow(() -> new VersionNotFoundException(version));
        boolean originalForceUpdate = appVersion.isForceUpdate();
        appVersion.setForceUpdate(!originalForceUpdate);
        appVersionRepository.save(appVersion);

        log.info("강제 업데이트 여부 변경: {} -> {} (버전: {})", originalForceUpdate, !originalForceUpdate, version);

        return !originalForceUpdate;
    }

    public void updateDownloadUrl(String version, String newUrl) {
        AppVersion appVersion = appVersionRepository.findByVersion(version)
                .orElseThrow(() -> new VersionNotFoundException(version));
        String oldUrl = appVersion.getDownloadUrl();
        appVersion.setDownloadUrl(newUrl);
        appVersionRepository.save(appVersion);

        log.info("다운로드 url 변경: {} -> {} (버전: {})", oldUrl, newUrl, version);
    }

    public void deleteVersion(String version) {
        AppVersion appVersion = appVersionRepository.findByVersion(version)
                .orElseThrow(() -> new VersionNotFoundException(version));
        appVersionRepository.delete(appVersion);

        log.info("버전 정보 삭제: {}", version);
    }
}
