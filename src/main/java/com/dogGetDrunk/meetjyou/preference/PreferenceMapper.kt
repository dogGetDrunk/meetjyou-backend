package com.dogGetDrunk.meetjyou.preference

import com.dogGetDrunk.meetjyou.post.dto.CompanionSpec
import kotlin.enums.enumEntries

fun List<CompPreference>.toCompanionSpec(strict: Boolean = true): CompanionSpec {
    val namesByType: Map<PreferenceType, Set<String>> = this // DTO 레벨에서 UniqueElements 검증을 하는데 Set이 필요할까?
        .map { it.preference }
        .groupBy({it.type}, {it.name})
        .mapValues { (_, names) -> names.toSet() }

    val genders: List<Gender> = namesByType[PreferenceType.GENDER].toEnumList(strict)
    val ages: List<Age> = namesByType[PreferenceType.AGE].toEnumList(strict)
    val personalities: List<Personality> = namesByType[PreferenceType.PERSONALITY].toEnumList(strict)
    val travelStyles: List<TravelStyle> = namesByType[PreferenceType.TRAVEL_STYLE].toEnumList(strict)
    val diets: List<Diet> = namesByType[PreferenceType.DIET].toEnumList(strict)
    val etcs: List<Etc> = namesByType[PreferenceType.ETC].toEnumList(strict)

    return CompanionSpec(
        gender = genders.sortedBy { it.name },
        age = ages.sortedBy { it.name },
        personalities = personalities.sortedBy { it.name },
        travelStyles = travelStyles.sortedBy { it.name },
        diet = diets.sortedBy { it.name },
        etc = etcs.sortedBy { it.name },
    )
}

inline fun <reified E : Enum<E>> Set<String>?.toEnumList(strict: Boolean): List<E> {
    if (this.isNullOrEmpty()) return emptyList()

    val entries = enumEntries<E>()
    val byName = entries.associateBy { it.name }

    return buildList {
        for (name in this@toEnumList) {
            val e = byName[name]
            if (e != null) {
                add(e)
            }
            else if (strict) {
                error("Unknown enum constant for ${E::class.simpleName}: $name")
            }
        }
    }
}
