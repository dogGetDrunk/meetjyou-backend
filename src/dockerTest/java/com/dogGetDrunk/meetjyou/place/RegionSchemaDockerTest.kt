package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.support.PlaceDb
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

/** AT-001 (region seed) and AT-012 (existing rows get 미지정 when V46 adds the destination columns). */
class RegionSchemaDockerTest : BehaviorSpec() {
    init {
        given("AT-001 Flyway로 지역 시드를 적용하면") {
            val db = PlaceDb()
            fun count(sql: String): Int = db.jdbc.queryForObject(sql, Int::class.java) ?: -1

            then("레벨별 건수와 계층이 맞다") {
                count("SELECT COUNT(*) FROM region") shouldBe 286
                count("SELECT COUNT(*) FROM region WHERE level = 'SIDO'") shouldBe 17
                count("SELECT COUNT(*) FROM region WHERE level = 'SIGUNGU'") shouldBe 269
                count("SELECT COUNT(*) FROM region WHERE level = 'SIDO' AND parent_code IS NOT NULL") shouldBe 0
                count("SELECT COUNT(*) FROM region WHERE level = 'SIGUNGU' AND parent_code IS NULL") shouldBe 0
                // A district hangs under a 시·도, or under a city that itself hangs under a 시·도.
                count(
                    """SELECT COUNT(*) FROM region r JOIN region p ON p.code = r.parent_code
                       LEFT JOIN region g ON g.code = p.parent_code
                       WHERE r.level = 'SIGUNGU' AND NOT (p.level = 'SIDO' OR (p.level = 'SIGUNGU' AND g.level = 'SIDO'))""",
                ) shouldBe 0
                count("SELECT COUNT(*) FROM region r JOIN region p ON p.code = r.parent_code WHERE LEFT(r.code, 2) <> LEFT(p.code, 2)") shouldBe 0
            }
            then("일반구 체인과 신설 지역이 있고 미지정은 검색 대상이 아니다") {
                parentOf(db, "41111") shouldBe "41110"
                parentOf(db, "41110") shouldBe "41"
                parentOf(db, "12210") shouldBe "12"
                val newDistricts = mapOf(
                    "28125" to "28", "28155" to "28", "28275" to "28",
                    "41591" to "41590", "41593" to "41590", "41595" to "41590", "41597" to "41590",
                )
                newDistricts.forEach { (code, parent) -> parentOf(db, code) shouldBe parent }
                count("SELECT COUNT(*) FROM region WHERE code = '00' AND searchable = 0") shouldBe 1
                count("SELECT COUNT(*) FROM region WHERE code IN ('29', '46', '29110')") shouldBe 0
            }
        }

        given("AT-012 V45까지 적용된 DB에 글·파티·계획서가 있을 때 V46을 적용하면") {
            then("세 테이블의 기존 행 모두 미지정 지역, 장소 없음") {
                val db = PlaceDb(target = "45")
                val userId = insertUser(db)
                db.jdbc.update(
                    """INSERT INTO party (uuid, itin_start, itin_finish, destination, joined, capacity, name)
                       VALUES (?, NOW(), NOW(), '자유', 1, 4, '파티')""",
                    UUID.randomUUID().toString(),
                )
                val partyId = db.jdbc.queryForObject("SELECT MAX(id) FROM party", Long::class.java)
                db.jdbc.update(
                    """INSERT INTO post (uuid, title, content, itin_start, itin_finish, location, author_id, party_id)
                       VALUES (?, '제목', '내용', NOW(), NOW(), '자유', ?, ?)""",
                    UUID.randomUUID().toString(), userId, partyId,
                )
                db.jdbc.update(
                    """INSERT INTO plan (uuid, itin_start, itin_finish, destination, center_lat, center_lng, owner_id)
                       VALUES (?, NOW(), NOW(), '자유', 37.0, 127.0, ?)""",
                    UUID.randomUUID().toString(), userId,
                )

                db.migrate()

                listOf("post", "party", "plan").forEach { table ->
                    db.jdbc.queryForList("SELECT region_code, place_id FROM $table").forEach {
                        it["region_code"] shouldBe "00"
                        it["place_id"] shouldBe null
                    }
                    db.jdbc.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java) shouldBe 1
                }
            }
        }
    }

    private fun parentOf(db: PlaceDb, code: String): String? =
        db.jdbc.queryForObject("SELECT parent_code FROM region WHERE code = ?", String::class.java, code)

    private fun insertUser(db: PlaceDb): Long {
        db.jdbc.update(
            "INSERT INTO user (uuid, email, nickname, auth_provider, external_id, role) VALUES (?, NULL, ?, 'KAKAO', ?, 'USER')",
            UUID.randomUUID().toString(), "u${UUID.randomUUID().toString().take(8)}", UUID.randomUUID().toString(),
        )
        return db.jdbc.queryForObject("SELECT MAX(id) FROM user", Long::class.java) ?: 0L
    }
}
