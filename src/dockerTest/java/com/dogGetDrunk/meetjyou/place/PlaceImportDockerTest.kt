package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.place.importer.PlaceImportState
import com.dogGetDrunk.meetjyou.place.importer.SbizZipReader
import com.dogGetDrunk.meetjyou.support.PlaceDb
import com.dogGetDrunk.meetjyou.support.SbizFixture
import com.dogGetDrunk.meetjyou.support.StoreRow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.util.UUID

/** AT-002 ~ AT-005: the import against real MySQL (staging SQL, multi-table UPDATE, chunked apply). */
class PlaceImportDockerTest : BehaviorSpec() {
    // Stable rows that keep each 시·도 above the 50% completeness bar while a test moves others around.
    private fun filler(prefix: String, count: Int, sigungu: String = "11680") =
        (1..count).map { StoreRow(key = "$prefix$it", sigungu = sigungu) }

    init {
        given("AT-002 대상·제외 업종과 미등록 지역이 섞인 파일") {
            then("대상 업종·등록 지역만 저장하고 나머지는 건너뜀으로 센다") {
                val db = PlaceDb()
                val rows = listOf(
                    StoreRow("CAFE", category = "I21201"),
                    StoreRow("CONV", category = "G20405"),
                    StoreRow("HOTEL", category = "I10101"),
                    StoreRow("CANTEEN", category = "I20701"),
                    StoreRow("DISCO", category = "I21102"),
                    StoreRow("DORM", category = "I10201"),
                    StoreRow("SUPER", category = "G20404"),
                    StoreRow("OLDGWANGJU", sigungu = "29110"),
                )
                val status = db.import(SbizFixture.zip(rows))

                status.state shouldBe PlaceImportState.SUCCEEDED
                status.read shouldBe 8
                status.staged shouldBe 3
                status.skipped shouldBe 5
                status.inserted shouldBe 3
                db.places().map { it.sourceKey } shouldContainExactlyInAnyOrder listOf("CAFE", "CONV", "HOTEL")
                db.places().all { it.active } shouldBe true
            }
        }

        given("AT-003 같은 파일을 다시 적재하면") {
            then("행 수·updated_at이 그대로이고 UPDATE가 0건") {
                val db = PlaceDb()
                val zip = SbizFixture.zip(filler("S", 5))
                db.import(zip)
                val before = db.jdbc.queryForList("SELECT id, updated_at FROM place ORDER BY id")

                val again = db.import(zip)

                again.state shouldBe PlaceImportState.SUCCEEDED
                again.updated shouldBe 0
                again.inserted shouldBe 0
                again.deactivated shouldBe 0
                db.jdbc.queryForList("SELECT id, updated_at FROM place ORDER BY id") shouldBe before
            }
            then("값이 바뀐 행만 갱신하고, 소속 지역이 바뀐 장소를 참조한 글의 지역은 그대로다") {
                val db = PlaceDb()
                db.import(SbizFixture.zip(filler("S", 5) + StoreRow("MOVE", sigungu = "11680")))
                val moved = db.place("MOVE")
                val planId = insertPlanReferencing(db, regionCode = "11680", placeId = moved.id)

                val status = db.import(
                    SbizFixture.zip(
                        filler("S", 4) + StoreRow("S5", name = "새 이름") + StoreRow("MOVE", sigungu = "11650"),
                    ),
                )

                status.updated shouldBe 2
                db.place("S5").name shouldBe "새 이름"
                db.place("MOVE").regionCode shouldBe "11650"
                db.place("MOVE").id shouldBe moved.id
                db.jdbc.queryForObject("SELECT region_code FROM plan WHERE id = ?", String::class.java, planId) shouldBe "11680"
            }
        }

        given("AT-004 다음 분기 파일에서 가게가 빠지거나 번호가 바뀌면") {
            then("빠진 가게는 비활성, 1:1로 맞는 새 번호만 기존 id를 이어받는다") {
                val db = PlaceDb()
                val longAddress = "X".repeat(150)
                db.import(
                    SbizFixture.zip(
                        filler("F", 20) + listOf(
                            StoreRow("GONE"),
                            StoreRow("OLD1", name = "같은가게", address = "주소1"),
                            StoreRow("ONE_TO_N", name = "둘로", address = "주소2"),
                            StoreRow("N1A", name = "합쳐짐", address = "주소3"),
                            StoreRow("N1B", name = "합쳐짐", address = "주소3"),
                            StoreRow("KEEP", name = "유지", address = "주소4"),
                            StoreRow("PREFIX_OLD", name = "앞부분", address = "${longAddress}1"),
                            StoreRow("COMEBACK", name = "돌아옴", address = "주소5"),
                        ),
                    ),
                )
                val before = db.places().associateBy { it.sourceKey }
                // COMEBACK leaves for one quarter first, so it is an inactive candidate below.
                db.import(SbizFixture.zip(filler("F", 20) + before.keys.filter { it != "COMEBACK" && !it.startsWith("F") }.map { key ->
                    val row = before.getValue(key)
                    StoreRow(key, name = row.name, address = row.roadAddress)
                }))
                db.place("COMEBACK").active shouldBe false

                val status = db.import(
                    SbizFixture.zip(
                        filler("F", 20) + listOf(
                            StoreRow("NEW1", name = "같은가게", address = "주소1"),
                            StoreRow("SPLIT_A", name = "둘로", address = "주소2"),
                            StoreRow("SPLIT_B", name = "둘로", address = "주소2"),
                            StoreRow("MERGED", name = "합쳐짐", address = "주소3"),
                            StoreRow("KEEP", name = "유지", address = "주소4"),
                            StoreRow("KEEP_TWIN", name = "유지", address = "주소4"),
                            StoreRow("PREFIX_NEW", name = "앞부분", address = "${longAddress}2"),
                            StoreRow("COMEBACK_NEW", name = "돌아옴", address = "주소5"),
                        ),
                    ),
                )

                status.state shouldBe PlaceImportState.SUCCEEDED
                status.rematched shouldBe 2
                db.place("NEW1").id shouldBe before.getValue("OLD1").id
                db.place("NEW1").active shouldBe true
                db.place("COMEBACK_NEW").id shouldBe before.getValue("COMEBACK").id
                db.place("COMEBACK_NEW").active shouldBe true
                db.place("KEEP").id shouldBe before.getValue("KEEP").id
                db.place("KEEP").active shouldBe true
                db.place("KEEP_TWIN").active shouldBe true
                listOf("GONE", "ONE_TO_N", "N1A", "N1B", "PREFIX_OLD").forEach { db.place(it).active shouldBe false }
                listOf("SPLIT_A", "SPLIT_B", "MERGED", "PREFIX_NEW").forEach {
                    val row = db.place(it)
                    row.active shouldBe true
                    (row.id > before.values.maxOf { old -> old.id }) shouldBe true
                }
            }
        }

        given("AT-005 온전한 파일로 볼 수 없으면") {
            val base = filler("SEOUL", 4, "11680") + filler("GG", 4, "41111")

            then("한 시·도가 활성 건수의 50% 미만이면 반영하지 않는다 (50%는 통과)") {
                val db = PlaceDb()
                db.import(SbizFixture.zip(base))
                val before = db.places()

                val short = db.import(SbizFixture.zip(filler("SEOUL", 4, "11680") + filler("GG", 1, "41111")))
                short.state shouldBe PlaceImportState.FAILED
                db.places() shouldBe before

                val half = db.import(SbizFixture.zip(filler("SEOUL", 4, "11680") + filler("GG", 2, "41111")))
                half.state shouldBe PlaceImportState.SUCCEEDED
                half.deactivated shouldBe 2
            }
            then("시·도 CSV 누락·필수 열 누락·CSV 없음은 실패하고 place는 그대로다") {
                val db = PlaceDb()
                db.import(SbizFixture.zip(base))
                val before = db.places()

                db.import(SbizFixture.zip(filler("SEOUL", 4, "11680"))).state shouldBe PlaceImportState.FAILED
                val noLat = SbizZipReader.REQUIRED_HEADERS.filter { it != SbizZipReader.LAT }
                db.import(SbizFixture.zip(base, headers = noLat)).state shouldBe PlaceImportState.FAILED
                db.import(SbizFixture.zipOf(mapOf("readme.txt" to "no csv"))).state shouldBe PlaceImportState.FAILED
                db.places() shouldBe before
            }
            then("첫 적재에서 통과한 가게가 0건이면 실패한다") {
                val db = PlaceDb()
                val status = db.import(SbizFixture.zip(listOf(StoreRow("CANTEEN", category = "I20701"))))
                status.state shouldBe PlaceImportState.FAILED
                db.places() shouldBe emptyList()
            }
            then("반영 도중 실패하면 비활성화 없이 멈추고, 같은 파일 재실행은 한 번에 성공한 결과와 같다") {
                val base6 = filler("R", 6)
                val next = filler("R", 5).mapIndexed { i, row -> row.copy(name = if (i == 2) BOOM else "갱신${row.key}") } +
                    StoreRow("R_NEW")

                val db = PlaceDb()
                db.import(SbizFixture.zip(base6), chunkSize = 2)
                failUpdatesNamed(db, BOOM)
                val failed = db.import(SbizFixture.zip(next), chunkSize = 2)

                failed.state shouldBe PlaceImportState.FAILED
                db.place("R6").active shouldBe true
                db.place("R1").name shouldBe "갱신R1"
                db.place("R4").name shouldBe "가게R4"
                db.places().none { it.sourceKey == "R_NEW" } shouldBe true

                dropTrigger(db)
                db.import(SbizFixture.zip(next), chunkSize = 2).state shouldBe PlaceImportState.SUCCEEDED

                val control = PlaceDb()
                control.import(SbizFixture.zip(base6), chunkSize = 2)
                control.import(SbizFixture.zip(next), chunkSize = 2)
                db.places() shouldBe control.places()
            }
        }
    }

    private fun insertPlanReferencing(db: PlaceDb, regionCode: String, placeId: Long): Long {
        db.jdbc.update(
            "INSERT INTO user (uuid, email, nickname, auth_provider, external_id, role) VALUES (?, NULL, ?, 'KAKAO', ?, 'USER')",
            UUID.randomUUID().toString(), "u${UUID.randomUUID().toString().take(8)}", UUID.randomUUID().toString(),
        )
        val ownerId = db.jdbc.queryForObject("SELECT MAX(id) FROM user", Long::class.java)
        db.jdbc.update(
            """INSERT INTO plan (uuid, itin_start, itin_finish, destination, center_lat, center_lng, owner_id, region_code, place_id)
               VALUES (?, NOW(), NOW(), '자유', 37.0, 127.0, ?, ?, ?)""",
            UUID.randomUUID().toString(), ownerId, regionCode, placeId,
        )
        return db.jdbc.queryForObject("SELECT MAX(id) FROM plan", Long::class.java) ?: 0L
    }

    // Root creates the trigger: with binary logging on, MySQL refuses it for a non-SUPER user.
    private fun failUpdatesNamed(db: PlaceDb, name: String) {
        db.mysql.rootConnection().use { connection ->
            connection.createStatement().use {
                it.execute("USE `${db.mysql.name}`")
                it.execute(
                    """CREATE TRIGGER fail_place_update BEFORE UPDATE ON place FOR EACH ROW
                       BEGIN IF NEW.name = '$name' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected'; END IF; END""",
                )
            }
        }
    }

    private fun dropTrigger(db: PlaceDb) {
        db.mysql.rootConnection().use { connection ->
            connection.createStatement().use {
                it.execute("USE `${db.mysql.name}`")
                it.execute("DROP TRIGGER fail_place_update")
            }
        }
    }

    private companion object {
        const val BOOM = "BOOM"
    }
}
