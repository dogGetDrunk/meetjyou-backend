package com.dogGetDrunk.meetjyou.support

import com.dogGetDrunk.meetjyou.place.Region
import com.dogGetDrunk.meetjyou.place.RegionCatalog
import com.dogGetDrunk.meetjyou.place.RegionLevel
import com.dogGetDrunk.meetjyou.place.RegionRepository
import com.dogGetDrunk.meetjyou.place.importer.PlaceImportProperties
import com.dogGetDrunk.meetjyou.place.importer.PlaceImportService
import com.dogGetDrunk.meetjyou.place.importer.PlaceImportState
import com.dogGetDrunk.meetjyou.place.importer.PlaceImportStatusResponse
import com.dogGetDrunk.meetjyou.place.importer.PlaceImportWriter
import com.dogGetDrunk.meetjyou.place.importer.SbizFileRef
import com.dogGetDrunk.meetjyou.place.importer.SbizFileSource
import com.dogGetDrunk.meetjyou.place.importer.SbizZipReader
import io.mockk.every
import io.mockk.mockk
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A fresh MySQL database migrated by Flyway, with the place import wired by hand around it. */
class PlaceDb(target: String? = null) {
    val mysql: MySqlDatabase = SharedMySql.newDatabase()
    val dataSource = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
    val jdbc = JdbcTemplate(dataSource)

    init {
        migrate(target)
    }

    fun migrate(target: String? = null) {
        val config = Flyway.configure().dataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        if (target != null) config.target(target)
        config.load().migrate()
    }

    fun regionCatalog(): RegionCatalog {
        val regions = jdbc.query("SELECT code, name, full_name, level, parent_code, searchable FROM region") { rs, _ ->
            Region(
                code = rs.getString(1), name = rs.getString(2), fullName = rs.getString(3),
                level = RegionLevel.valueOf(rs.getString(4)), parentCode = rs.getString(5), searchable = rs.getBoolean(6),
            )
        }
        val repository = mockk<RegionRepository> { every { findAll() } returns regions }
        return RegionCatalog(repository)
    }

    /** Runs one import of [zip] to completion and returns its final status. */
    fun import(zip: ByteArray, chunkSize: Int = DEFAULT_CHUNK): PlaceImportStatusResponse {
        val writer = PlaceImportWriter(jdbc, TransactionTemplate(DataSourceTransactionManager(dataSource)), chunkSize)
        val service = PlaceImportService(
            FixtureSource(zip), SbizZipReader(regionCatalog()), writer,
            PlaceImportProperties(chunkSize = chunkSize, stagingBatchSize = chunkSize), mockk(relaxed = true),
        )
        try {
            service.start()
            val deadline = System.currentTimeMillis() + WAIT_MILLIS
            while (service.latest().state == PlaceImportState.RUNNING) {
                check(System.currentTimeMillis() < deadline) { "Import did not finish in time" }
                Thread.sleep(POLL_MILLIS)
            }
            return service.latest()
        } finally {
            service.shutdown()
        }
    }

    fun places(): List<PlaceRow> = jdbc.query(
        "SELECT id, source_key, name, region_code, road_address, active FROM place ORDER BY id",
    ) { rs, _ ->
        PlaceRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6))
    }

    fun place(sourceKey: String): PlaceRow = places().single { it.sourceKey == sourceKey }

    private class FixtureSource(private val zip: ByteArray) : SbizFileSource {
        override fun <T : Any> download(consumer: (SbizFileRef, InputStream) -> T): T =
            consumer(SbizFileRef("FILE_TEST", "1", "fixture"), ByteArrayInputStream(zip))
    }

    private companion object {
        const val DEFAULT_CHUNK = 5_000
        const val WAIT_MILLIS = 30_000L
        const val POLL_MILLIS = 20L
    }
}

data class PlaceRow(
    val id: Long,
    val sourceKey: String,
    val name: String,
    val regionCode: String,
    val roadAddress: String,
    val active: Boolean,
)

/** One line of the SBIZ CSV; defaults are a 카페 in 서울 강남구. */
data class StoreRow(
    val key: String,
    val name: String = "가게$key",
    val sigungu: String = "11680",
    val category: String = "I21201",
    val address: String = "도로명 $key",
    val branch: String = "",
    val lat: String = "37.5",
    val lng: String = "127.0",
)

object SbizFixture {
    private val HEADERS = SbizZipReader.REQUIRED_HEADERS.toList()

    /** One CSV per 시·도, like the real file. */
    fun zip(rows: List<StoreRow>, headers: List<String> = HEADERS): ByteArray =
        zipOf(rows.groupBy { it.sigungu.take(2) }.mapKeys { "상가정보_${it.key}_202609.csv" }.mapValues { csv(it.value, headers) })

    fun zipOf(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out, Charsets.UTF_8).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun csv(rows: List<StoreRow>, headers: List<String> = HEADERS): String {
        val lines = rows.map { row ->
            val values = mapOf(
                SbizZipReader.SOURCE_KEY to row.key, SbizZipReader.NAME to row.name, SbizZipReader.BRANCH to row.branch,
                SbizZipReader.CATEGORY_CODE to row.category, SbizZipReader.CATEGORY_NAME to "업종${row.category}",
                SbizZipReader.SIDO_CODE to row.sigungu.take(2), SbizZipReader.SIGUNGU_CODE to row.sigungu,
                SbizZipReader.ROAD_ADDRESS to row.address, SbizZipReader.LNG to row.lng, SbizZipReader.LAT to row.lat,
            )
            headers.joinToString(",") { "\"${values.getValue(it)}\"" }
        }
        return (listOf(headers.joinToString(",") { "\"$it\"" }) + lines).joinToString("\n", postfix = "\n")
    }
}
