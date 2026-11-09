package com.dogGetDrunk.meetjyou.place.importer

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Where the SBIZ store file comes from and how an import writes it. The data.go.kr identifiers are
 * public (no key, no login); they change only if the portal re-registers the dataset.
 */
@ConfigurationProperties("place.import")
data class PlaceImportProperties(
    val baseUrl: String = "https://www.data.go.kr",
    val publicDataPk: String = "15083033",
    val publicDataDetailPk: String = "uddi:86f46f63-829a-46c1-abdd-d8e66846b6da",
    val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    // Per read, not for the whole ~350MB body.
    val readTimeout: Duration = DEFAULT_READ_TIMEOUT,
    // Rows per transaction while applying: keeps row locks short so post writes (FK checks on place)
    // never wait long behind an import.
    val chunkSize: Int = DEFAULT_CHUNK_SIZE,
    val stagingBatchSize: Int = DEFAULT_STAGING_BATCH_SIZE,
) {
    private companion object {
        val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(60)
        const val DEFAULT_CHUNK_SIZE = 5_000
        const val DEFAULT_STAGING_BATCH_SIZE = 1_000
    }
}
