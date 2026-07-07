package com.dogGetDrunk.meetjyou.version

import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.VersionNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.version.DuplicateVersionException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import java.time.Instant
import java.util.Optional

class AppVersionServiceTest : BehaviorSpec() {
    private val appVersionRepository: AppVersionRepository = mockk(relaxed = true)
    private val platformStoreUrlRepository: PlatformStoreUrlRepository = mockk(relaxed = true)
    private val sut = AppVersionService(appVersionRepository, platformStoreUrlRepository)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach {
            clearAllMocks()
            every { appVersionRepository.save(any<AppVersion>()) } answers { firstArg() }
            every { platformStoreUrlRepository.save(any<PlatformStoreUrl>()) } answers { firstArg() }
            every { platformStoreUrlRepository.findById(any()) } returns Optional.empty()
        }
        afterSpec { unmockkAll() }

        fun version(
            v: String,
            forceUpdate: Boolean = false,
            platform: Platform = Platform.IOS,
            message: String? = null,
            storeReleased: Boolean = true,
        ) = AppVersion(platform = platform, version = v, forceUpdate = forceUpdate, message = message, storeReleased = storeReleased)

        // ── checkVersion ─────────────────────────────────────────────────────

        given("checkVersion 호출 시") {
            `when`("클라이언트 버전이 최소 강제 버전보다 낮으면") {
                then("updateRequired=true, updateAvailable=true를 반환하고 강제 버전의 메시지를 담는다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns listOf(
                        version("1.0.0", forceUpdate = true, message = "필수 업데이트입니다"),
                        version("1.1.0"),
                    )

                    val result = sut.checkVersion(Platform.IOS, "0.9.0")

                    result.updateRequired shouldBe true
                    result.updateAvailable shouldBe true
                    result.latestVersion shouldBe "1.1.0"
                    result.message shouldBe "필수 업데이트입니다"
                }
            }

            `when`("클라이언트 버전이 최소 버전 이상이고 최신 버전보다 낮으면") {
                then("updateRequired=false, updateAvailable=true를 반환한다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns listOf(
                        version("1.0.0", forceUpdate = true),
                        version("1.1.0"),
                    )

                    val result = sut.checkVersion(Platform.IOS, "1.0.0")

                    result.updateRequired shouldBe false
                    result.updateAvailable shouldBe true
                    result.latestVersion shouldBe "1.1.0"
                }
            }

            `when`("클라이언트 버전이 최신 버전과 같으면") {
                then("updateRequired=false, updateAvailable=false, message=null을 반환한다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns listOf(
                        version("1.0.0", forceUpdate = true),
                        version("1.1.0"),
                    )

                    val result = sut.checkVersion(Platform.IOS, "1.1.0")

                    result.updateRequired shouldBe false
                    result.updateAvailable shouldBe false
                    result.message shouldBe null
                }
            }

            `when`("forceUpdate=true인 버전이 없으면") {
                then("updateRequired=false를 반환한다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns listOf(
                        version("1.0.0"),
                        version("1.1.0"),
                    )

                    val result = sut.checkVersion(Platform.IOS, "0.9.0")

                    result.updateRequired shouldBe false
                    result.updateAvailable shouldBe true
                }
            }

            `when`("등록된 버전이 없으면") {
                then("latestVersion=null, updateRequired=false, updateAvailable=false를 반환한다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns emptyList()

                    val result = sut.checkVersion(Platform.IOS, "1.0.0")

                    result.latestVersion shouldBe null
                    result.updateRequired shouldBe false
                    result.updateAvailable shouldBe false
                }
            }

            `when`("iOS와 Android 버전이 혼재할 때") {
                then("플랫폼별로 독립적으로 체크한다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns listOf(
                        version("2.0.0", forceUpdate = true, platform = Platform.IOS),
                    )
                    every { appVersionRepository.findAllByPlatform(Platform.ANDROID) } returns listOf(
                        version("1.0.0", platform = Platform.ANDROID),
                    )

                    val iosResult = sut.checkVersion(Platform.IOS, "1.5.0")
                    val androidResult = sut.checkVersion(Platform.ANDROID, "1.5.0")

                    iosResult.updateRequired shouldBe true
                    androidResult.updateRequired shouldBe false
                }
            }

            `when`("플랫폼의 스토어 URL이 등록되어 있으면") {
                then("응답의 downloadUrl에 담아 반환한다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns listOf(version("1.0.0"))
                    every { platformStoreUrlRepository.findById(Platform.IOS) } returns
                        Optional.of(PlatformStoreUrl(Platform.IOS, "https://apps.apple.com/app/id123"))

                    val result = sut.checkVersion(Platform.IOS, "1.0.0")

                    result.downloadUrl shouldBe "https://apps.apple.com/app/id123"
                }
            }

            `when`("같은 플랫폼을 두 번 조회하면") {
                then("두 번째 호출은 DB를 다시 조회하지 않는다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns listOf(version("1.0.0"))

                    sut.checkVersion(Platform.IOS, "0.9.0")
                    sut.checkVersion(Platform.IOS, "0.9.0")

                    verify(exactly = 1) { appVersionRepository.findAllByPlatform(Platform.IOS) }
                }
            }

            `when`("최신 버전이 아직 스토어 배포 확인 전(storeReleased=false)이면") {
                then("해당 버전은 latest/minimum 계산에서 제외된다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns listOf(
                        version("1.0.0", forceUpdate = true, storeReleased = true),
                        version("2.0.0", forceUpdate = true, storeReleased = false),
                    )

                    val result = sut.checkVersion(Platform.IOS, "1.0.0")

                    result.latestVersion shouldBe "1.0.0"
                    result.updateRequired shouldBe false
                }
            }

            `when`("클라이언트 버전이 숫자 x.y.z 형식이 아니면") {
                then("예외 없이 업데이트 불필요로 안전하게 처리한다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns listOf(
                        version("1.0.0", forceUpdate = true),
                    )

                    val result = sut.checkVersion(Platform.IOS, "1.0.0-beta")

                    result.updateRequired shouldBe false
                    result.updateAvailable shouldBe false
                    result.latestVersion shouldBe "1.0.0"
                }
            }
        }

        // ── addVersion ───────────────────────────────────────────────────────

        given("addVersion 호출 시") {
            val dto = AppVersionDto(
                platform = Platform.IOS,
                version = "1.0.0",
                forceUpdate = false,
                message = "버그 수정",
                releasedAt = Instant.now(),
            )

            `when`("동일한 platform+version이 없으면") {
                then("저장에 성공한다") {
                    every { appVersionRepository.findByVersionAndPlatform("1.0.0", Platform.IOS) } returns null

                    sut.addVersion(Platform.IOS, dto)

                    verify(exactly = 1) { appVersionRepository.save(any()) }
                }
            }

            `when`("동일한 platform+version이 이미 있으면") {
                then("DuplicateVersionException을 던진다") {
                    every { appVersionRepository.findByVersionAndPlatform("1.0.0", Platform.IOS) } returns version("1.0.0")

                    shouldThrow<DuplicateVersionException> {
                        sut.addVersion(Platform.IOS, dto)
                    }
                }
            }

            `when`("버전 형식이 숫자 x.y.z가 아니면") {
                then("InvalidInputException을 던진다") {
                    val invalidDto = dto.copy(version = "1.0.0-beta")

                    shouldThrow<InvalidInputException> {
                        sut.addVersion(Platform.IOS, invalidDto)
                    }
                }
            }

            `when`("버전이 등록되면") {
                then("해당 플랫폼의 캐시된 요약 정보가 무효화되어 다음 조회 시 DB를 다시 조회한다") {
                    every { appVersionRepository.findByVersionAndPlatform("1.0.0", Platform.IOS) } returns null
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns emptyList()

                    sut.checkVersion(Platform.IOS, "0.9.0")
                    sut.addVersion(Platform.IOS, dto)
                    sut.checkVersion(Platform.IOS, "0.9.0")

                    verify(exactly = 2) { appVersionRepository.findAllByPlatform(Platform.IOS) }
                }
            }

            `when`("버전을 등록하면") {
                then("storeReleased 요청값과 무관하게 항상 미배포(false) 상태로 생성된다") {
                    val releasedTrueDto = dto.copy(storeReleased = true)
                    every { appVersionRepository.findByVersionAndPlatform("1.0.0", Platform.IOS) } returns null

                    sut.addVersion(Platform.IOS, releasedTrueDto)

                    verify(exactly = 1) { appVersionRepository.save(match<AppVersion> { !it.storeReleased }) }
                }
            }
        }

        // ── toggleForceUpdate ─────────────────────────────────────────────────

        given("toggleForceUpdate 호출 시") {
            `when`("forceUpdate=false인 버전이면") {
                then("true로 전환하고 true를 반환한다") {
                    every { appVersionRepository.findByVersionAndPlatform("1.0.0", Platform.IOS) } returns version("1.0.0", forceUpdate = false)

                    val result = sut.toggleForceUpdate("1.0.0", Platform.IOS)

                    result shouldBe true
                }
            }

            `when`("forceUpdate=true인 버전이면") {
                then("false로 전환하고 false를 반환한다") {
                    every { appVersionRepository.findByVersionAndPlatform("1.0.0", Platform.IOS) } returns version("1.0.0", forceUpdate = true)

                    val result = sut.toggleForceUpdate("1.0.0", Platform.IOS)

                    result shouldBe false
                }
            }

            `when`("존재하지 않는 버전이면") {
                then("VersionNotFoundException을 던진다") {
                    every { appVersionRepository.findByVersionAndPlatform("9.9.9", Platform.IOS) } returns null

                    shouldThrow<VersionNotFoundException> {
                        sut.toggleForceUpdate("9.9.9", Platform.IOS)
                    }
                }
            }
        }

        // ── getLatestVersion ─────────────────────────────────────────────────

        given("getLatestVersion 호출 시") {
            `when`("storeReleased=true인 버전만 있으면") {
                then("해당 버전을 반환한다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns listOf(version("1.0.0", storeReleased = true))

                    val result = sut.getLatestVersion(Platform.IOS)

                    result.version shouldBe "1.0.0"
                }
            }

            `when`("등록된 버전이 모두 storeReleased=false이면") {
                then("VersionNotFoundException을 던진다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns listOf(version("1.0.0", storeReleased = false))

                    shouldThrow<VersionNotFoundException> {
                        sut.getLatestVersion(Platform.IOS)
                    }
                }
            }
        }

        // ── toggleStoreReleased ──────────────────────────────────────────────

        given("toggleStoreReleased 호출 시") {
            `when`("storeReleased=false인 버전이면") {
                then("true로 전환하고 true를 반환한다") {
                    every { appVersionRepository.findByVersionAndPlatform("1.0.0", Platform.IOS) } returns
                        version("1.0.0", storeReleased = false)

                    val result = sut.toggleStoreReleased("1.0.0", Platform.IOS)

                    result shouldBe true
                }
            }

            `when`("존재하지 않는 버전이면") {
                then("VersionNotFoundException을 던진다") {
                    every { appVersionRepository.findByVersionAndPlatform("9.9.9", Platform.IOS) } returns null

                    shouldThrow<VersionNotFoundException> {
                        sut.toggleStoreReleased("9.9.9", Platform.IOS)
                    }
                }
            }
        }

        // ── updateStoreUrl ───────────────────────────────────────────────────

        given("updateStoreUrl 호출 시") {
            `when`("해당 플랫폼의 URL이 없으면") {
                then("새로 생성해 저장한다") {
                    every { platformStoreUrlRepository.findById(Platform.IOS) } returns Optional.empty()

                    sut.updateStoreUrl(Platform.IOS, "https://apps.apple.com/app/id123")

                    verify(exactly = 1) {
                        platformStoreUrlRepository.save(match<PlatformStoreUrl> { it.platform == Platform.IOS && it.downloadUrl == "https://apps.apple.com/app/id123" })
                    }
                }
            }

            `when`("해당 플랫폼의 URL이 이미 있으면") {
                then("덮어써서 저장한다") {
                    every { platformStoreUrlRepository.findById(Platform.IOS) } returns
                        Optional.of(PlatformStoreUrl(Platform.IOS, "https://old-url.com"))

                    sut.updateStoreUrl(Platform.IOS, "https://new-url.com")

                    verify(exactly = 1) {
                        platformStoreUrlRepository.save(match<PlatformStoreUrl> { it.platform == Platform.IOS && it.downloadUrl == "https://new-url.com" })
                    }
                }
            }
        }

        // ── deleteVersion ─────────────────────────────────────────────────────

        given("deleteVersion 호출 시") {
            `when`("버전이 존재하면") {
                then("삭제에 성공한다") {
                    val v = version("1.0.0")
                    every { appVersionRepository.findByVersionAndPlatform("1.0.0", Platform.IOS) } returns v

                    sut.deleteVersion("1.0.0", Platform.IOS)

                    verify(exactly = 1) { appVersionRepository.delete(v) }
                }
            }

            `when`("버전이 존재하지 않으면") {
                then("VersionNotFoundException을 던진다") {
                    every { appVersionRepository.findByVersionAndPlatform("9.9.9", Platform.IOS) } returns null

                    shouldThrow<VersionNotFoundException> {
                        sut.deleteVersion("9.9.9", Platform.IOS)
                    }
                }
            }
        }
    }
}
