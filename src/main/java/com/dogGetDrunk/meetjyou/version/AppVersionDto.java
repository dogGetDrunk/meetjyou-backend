package com.dogGetDrunk.meetjyou.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppVersionDto {

    private String version;
    private boolean forceUpdate;
    private String downloadUrl;
    private LocalDateTime releaseDate;

    public static AppVersionDto fromEntity(AppVersion appVersion) {
        return AppVersionDto.builder()
                .version(appVersion.getVersion())
                .forceUpdate(appVersion.isForceUpdate())
                .downloadUrl(appVersion.getDownloadUrl())
                .releaseDate(appVersion.getReleaseDate())
                .build();
    }
}
