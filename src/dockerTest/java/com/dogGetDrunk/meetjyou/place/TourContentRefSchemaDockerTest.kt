package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.support.PlaceDb
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.sql.SQLException
import java.util.UUID

private const val CHECK_CONSTRAINT_VIOLATED = 3819
private const val FK_PARENT_ROW_MISSING = 1452
private const val MISSING_PLACE_ID = 999_999_999L

/**
 * AT-001: V48~V50 add `tour_content_id` to post / party / plan with a CHECK that keeps a row on one
 * place source. MySQL only: H2 builds its schema from the entities and has no CHECK.
 */
class TourContentRefSchemaDockerTest : BehaviorSpec() {
    private val tables = listOf("post", "party", "plan")

    init {
        given("AT-001 V48~V50을 적용하면") {
            val db = PlaceDb()
            val rows = Rows(db)
            val storeId = rows.insertPlace()

            then("세 테이블에 nullable VARCHAR(20) 컬럼이 생긴다") {
                tables.forEach { table ->
                    val column = db.jdbc.queryForMap(
                        """SELECT data_type, character_maximum_length, is_nullable FROM information_schema.columns
                           WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'tour_content_id'""",
                        table,
                    )
                    column["DATA_TYPE"] shouldBe "varchar"
                    (column["CHARACTER_MAXIMUM_LENGTH"] as Number).toInt() shouldBe 20
                    column["IS_NULLABLE"] shouldBe "YES"
                }
            }
            then("store와 관광공사 id를 함께 넣으면 CHECK 위반(3819)") {
                tables.forEach { table ->
                    val error = shouldThrowAny { rows.insert(table, storeId, "126508") }
                    errorCodeOf(error) shouldBe CHECK_CONSTRAINT_VIOLATED
                }
            }
            then("하나만 넣으면 성공") {
                tables.forEach { table ->
                    rows.insert(table, storeId, null)
                    rows.insert(table, null, "126508")
                }
            }
            then("한 UPDATE로 출처를 바꾸는 것은 두 방향 모두 성공") {
                tables.forEach { table ->
                    val id = rows.insert(table, storeId, null)
                    db.jdbc.update("UPDATE $table SET place_id = NULL, tour_content_id = '126508' WHERE id = ?", id)
                    db.jdbc.update("UPDATE $table SET place_id = ?, tour_content_id = NULL WHERE id = ?", storeId, id)
                    db.jdbc.queryForObject("SELECT place_id FROM $table WHERE id = ?", Long::class.java, id) shouldBe
                        storeId
                }
            }
            then("기존 place FK는 그대로 동작") {
                tables.forEach { table ->
                    val error = shouldThrowAny { rows.insert(table, MISSING_PLACE_ID, null) }
                    errorCodeOf(error) shouldBe FK_PARENT_ROW_MISSING
                }
            }
        }
    }

    private fun errorCodeOf(error: Throwable): Int? =
        generateSequence(error) { it.cause }.filterIsInstance<SQLException>().firstOrNull()?.errorCode

    /** Minimal rows: only the NOT NULL columns of each table, plus the two place columns under test. */
    private class Rows(private val db: PlaceDb) {
        private val userId: Long by lazy { insertUser() }
        private val partyId: Long by lazy { insert("party", null, null) }

        fun insertPlace(): Long {
            db.jdbc.update(
                """INSERT INTO place (source, source_key, name, search_name, category_code, category_name,
                                      region_code, road_address, lat, lng, active)
                   VALUES ('SBIZ', ?, '가게', '가게', 'I21201', '카페', '11', '도로명', 37.5, 127.0, 1)""",
                UUID.randomUUID().toString().take(20),
            )
            return lastId("place")
        }

        fun insert(table: String, placeId: Long?, tourContentId: String?): Long {
            val uuid = UUID.randomUUID().toString()
            when (table) {
                "party" -> db.jdbc.update(
                    """INSERT INTO party (uuid, itin_start, itin_finish, destination, joined, capacity, name,
                                          place_id, tour_content_id)
                       VALUES (?, NOW(), NOW(), '자유', 1, 4, '파티', ?, ?)""",
                    uuid, placeId, tourContentId,
                )
                "post" -> db.jdbc.update(
                    """INSERT INTO post (uuid, title, content, itin_start, itin_finish, location, author_id, party_id,
                                         place_id, tour_content_id)
                       VALUES (?, '제목', '내용', NOW(), NOW(), '자유', ?, ?, ?, ?)""",
                    uuid, userId, partyId, placeId, tourContentId,
                )
                else -> db.jdbc.update(
                    """INSERT INTO plan (uuid, itin_start, itin_finish, destination, center_lat, center_lng, owner_id,
                                         place_id, tour_content_id)
                       VALUES (?, NOW(), NOW(), '자유', 37.0, 127.0, ?, ?, ?)""",
                    uuid, userId, placeId, tourContentId,
                )
            }
            return db.jdbc.queryForObject("SELECT id FROM $table WHERE uuid = ?", Long::class.java, uuid) ?: 0L
        }

        private fun insertUser(): Long {
            db.jdbc.update(
                """INSERT INTO user (uuid, email, nickname, auth_provider, external_id, role)
                   VALUES (?, NULL, ?, 'KAKAO', ?, 'USER')""",
                UUID.randomUUID().toString(), "u${UUID.randomUUID().toString().take(8)}", UUID.randomUUID().toString(),
            )
            return lastId("user")
        }

        private fun lastId(table: String): Long =
            db.jdbc.queryForObject("SELECT MAX(id) FROM $table", Long::class.java) ?: 0L
    }
}
