package com.dogGetDrunk.meetjyou.preference

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PreferenceRepository : JpaRepository<Preference, Long> {
    fun findByName(name: String): Preference?
    fun findAllByTypeAndNameIn(type: PreferenceType, names: Collection<String>): List<Preference>
}
