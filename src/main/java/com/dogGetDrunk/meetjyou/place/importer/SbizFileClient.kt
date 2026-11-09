package com.dogGetDrunk.meetjyou.place.importer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.io.InputStream

/** The file a download resolves to; [dataName] carries the data date (e.g. `…_20260930`). */
data class SbizFileRef(
    val atchFileId: String,
    val fileDetailSn: String,
    val dataName: String,
)

/** Where an import gets the SBIZ zip; tests swap in a fixture file. */
interface SbizFileSource {
    /** Runs [consumer] on the zip body while it is open. */
    fun <T : Any> download(consumer: (SbizFileRef, InputStream) -> T): T
}

/**
 * Downloads the SBIZ store zip the way the data.go.kr page does: resolve the file id, check the
 * download limit, then stream the file. No login or cookie is needed (checked 2026-11-09).
 * The endpoints are portal internals, so a portal change breaks this — the import then fails
 * without touching `place`.
 */
class SbizFileClient(
    private val props: PlaceImportProperties,
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
) : SbizFileSource {
    override fun <T : Any> download(consumer: (SbizFileRef, InputStream) -> T): T {
        val file = resolveFile()
        checkLimit(file)
        return restClient.get()
            .uri {
                it.path(DOWNLOAD_PATH)
                    .queryParam("atchFileId", file.atchFileId)
                    .queryParam("fileDetailSn", file.fileDetailSn)
                    .build()
            }
            .exchange { _, response ->
                if (!response.statusCode.is2xxSuccessful) {
                    throw PlaceImportException("File download failed with ${response.statusCode}")
                }
                consumer(file, response.body)
            }
            ?: throw PlaceImportException("File download returned nothing")
    }

    private fun resolveFile(): SbizFileRef {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("publicDataPk", props.publicDataPk)
            add("publicDataDetailPk", props.publicDataDetailPk)
            add("atchFileId", "")
            add("fileDetailSn", DEFAULT_FILE_DETAIL_SN)
            add("publicDataTyCode", PUBLIC_DATA_TYPE_CODE)
        }
        val json = postForm(RESOLVE_PATH, form)
        if (!json.path("status").asBoolean(false)) {
            throw PlaceImportException("File lookup was refused: ${json.path("error").asText("no reason")}")
        }
        val atchFileId = json.path("atchFileId").asText("")
        if (atchFileId.isBlank()) throw PlaceImportException("File lookup returned no atchFileId")
        return SbizFileRef(
            atchFileId = atchFileId,
            fileDetailSn = json.path("fileDetailSn").asText(DEFAULT_FILE_DETAIL_SN),
            dataName = json.path("dataSetFileDetailInfo").path("dataNm").asText(""),
        )
    }

    private fun checkLimit(file: SbizFileRef) {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("atchFileId", file.atchFileId)
            add("fileDetailSn", file.fileDetailSn)
        }
        // A 403 here (rate limited) surfaces as an HTTP client exception and fails the run too.
        if (postForm(LIMIT_PATH, form).path("needCaptcha").asBoolean(true)) {
            throw PlaceImportException("Download needs a captcha (download limit reached)")
        }
    }

    // The portal answers JSON under varying content types, often without a charset (a String body would
    // then decode as ISO-8859-1 and garble Korean), so the bytes are parsed as UTF-8 JSON directly.
    private fun postForm(path: String, form: LinkedMultiValueMap<String, String>): JsonNode {
        val body = restClient.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(ByteArray::class.java)
            ?: throw PlaceImportException("Empty response from $path")
        return objectMapper.readTree(body.toString(Charsets.UTF_8))
    }

    companion object {
        const val RESOLVE_PATH = "/tcs/dss/selectFileDataDownload.do"
        const val LIMIT_PATH = "/cmm/cmm/check-limit.json"
        const val DOWNLOAD_PATH = "/cmm/cmm/fileDownload.do"
        private const val DEFAULT_FILE_DETAIL_SN = "1"
        private const val PUBLIC_DATA_TYPE_CODE = "PR0051"
    }
}
