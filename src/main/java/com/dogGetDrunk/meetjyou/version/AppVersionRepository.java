package com.dogGetDrunk.meetjyou.version;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppVersionRepository extends JpaRepository<AppVersion, Long> {
    Optional<AppVersion> findFirstByOrderByReleasedAtDesc();
    Optional<AppVersion> findByVersion(String version);
}
