package com.dogGetDrunk.meetjyou.preference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    @Modifying
    @Query("DELETE FROM UserPreference up WHERE up.user.id = :userId AND up.preference.type = :type")
    void deleteByUserIdAndType(@Param("userId") Long userId, @Param("type") int type);

    @Query("SELECT up.preference FROM UserPreference up WHERE up.user.id = :userId AND up.preference.type = :type")
    Optional<Preference> findPreferenceByUserIdAndType(@Param("userId") Long userId, @Param("type") int type);

    @Query("SELECT up.preference FROM UserPreference up WHERE up.user.id = :userId AND up.preference.type = :type")
    List<Preference> findPreferencesByUserIdAndType(@Param("userId") Long userId, @Param("type") int type);
}
