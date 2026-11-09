package com.dogGetDrunk.meetjyou.place.importer

import com.dogGetDrunk.meetjyou.place.RegionCatalog
import com.dogGetDrunk.meetjyou.place.RegionLevel
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/** One store kept by the filter, shaped like a `place_import_staging` row. */
data class StagingRow(
    val sourceKey: String,
    val name: String,
    val branchName: String?,
    val searchName: String,
    val categoryCode: String,
    val categoryName: String,
    val regionCode: String,
    val sidoCode: String,
    val roadAddress: String,
    val lat: Double,
    val lng: Double,
)

class ReadStats {
    var csvFiles = 0
    var read = 0L
    var skipped = 0L
}

/**
 * Streams the SBIZ zip (one CSV per 시·도, UTF-8, every field quoted) without unpacking it to disk.
 * Rows outside the travel categories or in an unknown district are skipped and counted.
 */
class SbizZipReader(
    private val regionCatalog: RegionCatalog,
) {
    fun read(input: InputStream, batchSize: Int, sink: (List<StagingRow>) -> Unit): ReadStats {
        val stats = ReadStats()
        val batch = ArrayList<StagingRow>(batchSize)
        val zip = ZipInputStream(input, Charsets.UTF_8)
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name.endsWith(CSV_SUFFIX)) {
                stats.csvFiles++
                readCsv(entry.name, NonClosingInputStream(zip), stats) { row ->
                    batch.add(row)
                    if (batch.size >= batchSize) flush(batch, sink)
                }
            }
            entry = zip.nextEntry
        }
        flush(batch, sink)
        if (stats.csvFiles == 0) throw PlaceImportException("The zip has no CSV file")
        return stats
    }

    private fun readCsv(entryName: String, input: InputStream, stats: ReadStats, emit: (StagingRow) -> Unit) {
        val parser = CSV_FORMAT.parse(InputStreamReader(input, Charsets.UTF_8))
        val missing = REQUIRED_HEADERS - parser.headerNames.toSet()
        if (missing.isNotEmpty()) throw PlaceImportException("$entryName is missing columns $missing")
        for (record in parser) {
            stats.read++
            val row = toRow(record)
            if (row == null) stats.skipped++ else emit(row)
        }
    }

    private fun toRow(record: CSVRecord): StagingRow? {
        val categoryCode = record.get(CATEGORY_CODE)
        val regionCode = record.get(SIGUNGU_CODE)
        val lat = record.get(LAT).toDoubleOrNull()
        val lng = record.get(LNG).toDoubleOrNull()
        val name = record.get(NAME).trim()
        val keep = SbizCategoryPolicy.isTravelCategory(categoryCode) && isKnownDistrict(regionCode) && name.isNotEmpty()
        if (!keep || lat == null || lng == null) return null
        val branch = record.get(BRANCH).trim().ifEmpty { null }
        return StagingRow(
            sourceKey = record.get(SOURCE_KEY).take(SOURCE_KEY_MAX),
            name = name.take(NAME_MAX),
            branchName = branch?.take(BRANCH_MAX),
            searchName = RegionCatalog.compact("$name${branch.orEmpty()}").take(SEARCH_NAME_MAX),
            categoryCode = categoryCode,
            categoryName = record.get(CATEGORY_NAME).take(CATEGORY_NAME_MAX),
            regionCode = regionCode,
            sidoCode = regionCode.take(SIDO_CODE_LENGTH),
            roadAddress = record.get(ROAD_ADDRESS).take(ROAD_ADDRESS_MAX),
            lat = lat,
            lng = lng,
        )
    }

    private fun isKnownDistrict(code: String): Boolean =
        regionCatalog.find(code)?.let { it.level == RegionLevel.SIGUNGU && it.searchable } ?: false

    private fun flush(batch: MutableList<StagingRow>, sink: (List<StagingRow>) -> Unit) {
        if (batch.isEmpty()) return
        sink(batch.toList())
        batch.clear()
    }

    // The CSV reader closes what it reads; closing the zip there would end the remaining entries.
    private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    companion object {
        private const val CSV_SUFFIX = ".csv"
        private val CSV_FORMAT: CSVFormat = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()

        const val SOURCE_KEY = "상가업소번호"
        const val NAME = "상호명"
        const val BRANCH = "지점명"
        const val CATEGORY_CODE = "상권업종소분류코드"
        const val CATEGORY_NAME = "상권업종소분류명"
        const val SIDO_CODE = "시도코드"
        const val SIGUNGU_CODE = "시군구코드"
        const val ROAD_ADDRESS = "도로명주소"
        const val LNG = "경도"
        const val LAT = "위도"
        val REQUIRED_HEADERS = setOf(
            SOURCE_KEY, NAME, BRANCH, CATEGORY_CODE, CATEGORY_NAME, SIDO_CODE, SIGUNGU_CODE, ROAD_ADDRESS, LNG, LAT,
        )

        // Column sizes of place / place_import_staging.
        private const val SOURCE_KEY_MAX = 30
        private const val NAME_MAX = 200
        private const val BRANCH_MAX = 100
        private const val SEARCH_NAME_MAX = 300
        private const val CATEGORY_NAME_MAX = 50
        private const val ROAD_ADDRESS_MAX = 200
        private const val SIDO_CODE_LENGTH = 2
    }
}
