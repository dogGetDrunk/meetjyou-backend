package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.common.exception.GlobalExceptionHandler
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.TermsNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.terms.TermsContentVerificationException
import com.dogGetDrunk.meetjyou.terms.dto.GetTermsContentUrlResponse
import com.dogGetDrunk.meetjyou.terms.dto.GetTermsResponse
import com.dogGetDrunk.meetjyou.terms.dto.TermsUploadPar
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

class TermsControllerTest : BehaviorSpec() {
    private val termsService = mockk<TermsService>()
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private val mockMvc = MockMvcBuilders
        .standaloneSetup(TermsController(termsService))
        .setControllerAdvice(GlobalExceptionHandler(mockk<DiscordAlertService>(relaxed = true)))
        .build()

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }
        afterSpec { unmockkAll() }

        // ── GET /terms/active ────────────────────────────────────────────────

        given("GET /terms/active 호출 시") {
            `when`("활성 약관이 존재하면") {
                then("200과 활성 약관 목록을 반환한다") {
                    val response = listOf(
                        GetTermsResponse(
                            termsUuid = UUID.randomUUID().toString(),
                            type = TermsType.TERMS_OF_SERVICE,
                            version = "1.0",
                            displayText = "이용약관",
                            required = true,
                            hasContent = true,
                        ),
                    )
                    every { termsService.getAllActiveTerms() } returns response

                    mockMvc.get("/terms/active")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$[0].type") { value("TERMS_OF_SERVICE") }
                            jsonPath("$[0].required") { value(true) }
                        }
                }
            }
        }

        // ── GET /terms/{termsUuid}/content-url ──────────────────────────────

        given("GET /terms/{termsUuid}/content-url 호출 시") {
            val termsUuid = UUID.randomUUID().toString()

            `when`("존재하는 약관 UUID이면") {
                then("200과 다운로드 URL을 반환한다") {
                    val response = GetTermsContentUrlResponse(
                        termsUuid = termsUuid,
                        downloadUrl = "https://example.com/download",
                        httpMethod = "GET",
                        expiresAt = Instant.now(),
                    )
                    every { termsService.getTermsContentUrl(termsUuid) } returns response

                    mockMvc.get("/terms/$termsUuid/content-url")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.downloadUrl") { value("https://example.com/download") }
                        }
                }
            }

            `when`("존재하지 않는 약관 UUID이면") {
                then("404를 반환한다") {
                    every { termsService.getTermsContentUrl(termsUuid) } throws TermsNotFoundException(termsUuid)

                    mockMvc.get("/terms/$termsUuid/content-url")
                        .andExpect { status { isNotFound() } }
                }
            }
        }

        // ── POST /terms/content/par/upload ──────────────────────────────────

        given("POST /terms/content/par/upload 호출 시") {
            `when`("약관 타입과 버전이 주어지면") {
                then("200과 오브젝트 키·업로드 PAR를 반환한다") {
                    val response = TermsUploadPar(
                        objectKey = "terms/service/v2.0.html",
                        par = ParResponse(
                            url = "https://example.com/upload",
                            httpMethod = "PUT",
                            expiresAt = Instant.now(),
                        ),
                    )
                    every {
                        termsService.createContentUploadPar(TermsType.TERMS_OF_SERVICE, "2.0")
                    } returns response

                    mockMvc.post("/terms/content/par/upload?type=TERMS_OF_SERVICE&version=2.0")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.objectKey") { value(response.objectKey) }
                            jsonPath("$.par.httpMethod") { value("PUT") }
                        }
                }
            }
        }

        // ── POST /terms ──────────────────────────────────────────────────────

        given("POST /terms 호출 시") {
            `when`("필수 필드가 비어 있으면") {
                then("400을 반환한다") {
                    val invalidBody = """
                        {
                          "type": "TERMS_OF_SERVICE",
                          "version": "",
                          "displayText": "이용약관",
                          "required": true,
                          "contentHash": "hash"
                        }
                    """.trimIndent()

                    mockMvc.post("/terms") {
                        contentType = MediaType.APPLICATION_JSON
                        content = invalidBody
                    }.andExpect { status { isBadRequest() } }
                }
            }

            `when`("유효한 요청이면") {
                then("200과 게시된 약관 정보를 반환한다") {
                    val validBody = """
                        {
                          "type": "TERMS_OF_SERVICE",
                          "version": "2.0",
                          "displayText": "이용약관",
                          "required": true,
                          "contentHash": "hash"
                        }
                    """.trimIndent()

                    val response = GetTermsResponse(
                        termsUuid = UUID.randomUUID().toString(),
                        type = TermsType.TERMS_OF_SERVICE,
                        version = "2.0",
                        displayText = "이용약관",
                        required = true,
                        hasContent = true,
                    )
                    every { termsService.publishTerms(any()) } returns response

                    mockMvc.post("/terms") {
                        contentType = MediaType.APPLICATION_JSON
                        content = validBody
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.version") { value("2.0") }
                    }
                }
            }

            `when`("업로드된 본문 오브젝트 검증에 실패하면") {
                then("400을 반환한다") {
                    val validBody = """
                        {
                          "type": "TERMS_OF_SERVICE",
                          "version": "2.0",
                          "displayText": "이용약관",
                          "required": true,
                          "contentHash": "hash"
                        }
                    """.trimIndent()

                    every {
                        termsService.publishTerms(any())
                    } throws TermsContentVerificationException("terms/tos-v2.html")

                    mockMvc.post("/terms") {
                        contentType = MediaType.APPLICATION_JSON
                        content = validBody
                    }.andExpect { status { isBadRequest() } }
                }
            }
        }
    }
}
