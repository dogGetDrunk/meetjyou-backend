package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.place.dto.PlaceSearchRow
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

/**
 * Place search on MySQL (ngram FULLTEXT on `search_name`, 2-char tokens). MySQL only: the H2 test schema
 * has no FULLTEXT index, so this is covered by dockerTest.
 *
 * None of the queries sorts: a sort reads every matching row first, and a common word like '카페' matches
 * tens of thousands of rows spread over the whole table, far more than the buffer pool holds. Without
 * one, each query stops at LIMIT.
 *
 * The region-index queries carry NO_MRR: with several region codes (several index ranges) MySQL picks
 * Multi-Range Read, which collects every qualifying entry before reading rows and so never stops at LIMIT
 * (measured: all 623 matches of a 28k-row 시·도 read instead of 19). They also force the index: past 200
 * IN values (eq_range_index_dive_limit) MySQL estimates rows from index statistics, and with stale or
 * missing statistics it chose a full table scan for the nationwide exact-name lookup.
 */
@Repository
class PlaceSearchRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    /** Places named exactly [exact], through idx_place_region_search (region_code leads, so codes are required). */
    fun findExact(exact: String, regionCodes: Set<String>, limit: Int): List<PlaceSearchRow> {
        val params = MapSqlParameterSource()
            .addValue("exact", exact)
            .addValue("regionCodes", regionCodes)
            .addValue("limit", limit)
        val sql = """
            SELECT $NO_MRR $COLUMNS FROM place $REGION_INDEX
            WHERE region_code IN (:regionCodes) AND active = 1 AND search_name = :exact
            LIMIT :limit
        """.trimIndent()
        return jdbc.query(sql, params) { rs, _ -> toRow(rs) }
    }

    /**
     * Full-text path: the required terms are answered from the index alone, and LIKE confirms the tokens
     * are adjacent on the rows read. For no region filter or a large one, where matches are dense.
     */
    fun searchFullText(query: PlaceSearchQuery, regionCodes: Set<String>?, limit: Int): List<PlaceSearchRow> {
        val terms = query.terms ?: return emptyList()
        val params = likeParams(query, limit).addValue("terms", terms)
        val regionFilter = if (regionCodes == null) "" else "AND region_code IN (:regionCodes)"
        if (regionCodes != null) params.addValue("regionCodes", regionCodes)
        val sql = """
            SELECT $COLUMNS FROM place
            WHERE MATCH(search_name) AGAINST (:terms IN BOOLEAN MODE)
              AND search_name LIKE :pattern ESCAPE '${PlaceSearchQuery.LIKE_ESCAPE}'
              AND search_name <> :exact
              AND active = 1
              $regionFilter
            LIMIT :limit
        """.trimIndent()
        return jdbc.query(sql, params) { rs, _ -> toRow(rs) }
    }

    /**
     * Region index path: scans only the region's entries of idx_place_region_search and reads a row only
     * when its name matches. For a small region, where full-text matches would be mostly elsewhere.
     */
    fun searchRegionIndex(query: PlaceSearchQuery, regionCodes: Set<String>, limit: Int): List<PlaceSearchRow> {
        val params = likeParams(query, limit).addValue("regionCodes", regionCodes)
        val sql = """
            SELECT $NO_MRR $COLUMNS FROM place $REGION_INDEX
            WHERE region_code IN (:regionCodes) AND active = 1
              AND search_name LIKE :pattern ESCAPE '${PlaceSearchQuery.LIKE_ESCAPE}'
              AND search_name <> :exact
            LIMIT :limit
        """.trimIndent()
        return jdbc.query(sql, params) { rs, _ -> toRow(rs) }
    }

    private fun likeParams(query: PlaceSearchQuery, limit: Int) = MapSqlParameterSource()
        .addValue("pattern", query.likePattern)
        .addValue("exact", query.exact)
        .addValue("limit", limit)

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
        private const val NO_MRR = "/*+ NO_MRR(place) */"
        private const val REGION_INDEX = "FORCE INDEX (idx_place_region_search)"
        private const val COLUMNS = "id, name, branch_name, category_name, road_address, lat, lng, region_code"
    }
}
