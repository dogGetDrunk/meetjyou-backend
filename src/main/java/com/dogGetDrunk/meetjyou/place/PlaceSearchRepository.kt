package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.place.dto.PlaceSearchRow
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

/**
 * MySQL full-text search over `place.search_name` (ngram parser, 2-char tokens). MySQL only: the
 * H2 test schema has no FULLTEXT index, so this is covered by dockerTest.
 */
@Repository
class PlaceSearchRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    /**
     * [compactQuery] has its whitespace removed, like `search_name`, and is matched as a phrase so
     * "밀본 그레이츠" and "밀본그레이츠" find the same store. An exact name match comes first.
     */
    fun search(compactQuery: String, regionCodes: Set<String>?, limit: Int): List<PlaceSearchRow> {
        val params = MapSqlParameterSource()
            .addValue("phrase", "\"${compactQuery.replace(QUOTE, "")}\"")
            .addValue("exact", compactQuery)
            .addValue("limit", limit)
        val regionFilter = if (regionCodes == null) "" else "AND region_code IN (:regionCodes)"
        if (regionCodes != null) params.addValue("regionCodes", regionCodes)
        val sql = """
            SELECT id, name, branch_name, category_name, road_address, lat, lng, region_code
            FROM place
            WHERE MATCH(search_name) AGAINST (:phrase IN BOOLEAN MODE)
              AND active = 1
              $regionFilter
            ORDER BY (search_name = :exact) DESC, id
            LIMIT :limit
        """.trimIndent()
        return jdbc.query(sql, params) { rs, _ -> toRow(rs) }
    }

    private fun toRow(rs: ResultSet) = PlaceSearchRow(
        id = rs.getLong("id"),
        name = rs.getString("name"),
        branchName = rs.getString("branch_name"),
        categoryName = rs.getString("category_name"),
        roadAddress = rs.getString("road_address"),
        lat = rs.getDouble("lat"),
        lng = rs.getDouble("lng"),
        regionCode = rs.getString("region_code"),
    )

    companion object {
        // A double quote would end the boolean-mode phrase early; other operators are literal inside it.
        private const val QUOTE = "\""
    }
}
