package com.dogGetDrunk.meetjyou.preference;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferenceRepository extends JpaRepository<Preference, Long> {
    Preference findByName(String name);
}
