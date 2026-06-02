package com.dogGetDrunk.meetjyou.version

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.VersionNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.version.DuplicateVersionException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.unmockkAll
import java.time.Instant

class AppVersionServiceTest : BehaviorSpec() {
    private val appVersionRepository: AppVersionRepository = mockk(relaxed = true)
    private val sut = AppVersionService(appVersionRepository)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach {
            clearAllMocks()
            every { appVersionRepository.save(any<AppVersion>()) } answers { firstArg() }
        }
        afterSpec { unmockkAll() }

        fun version(v: String, forceUpdate: Boolean = false, platform: Platform = Platform.IOS) =
            AppVersion(platform = platform, version = v, forceUpdate = forceUpdate, downloadUrl = "https://example.com")

        // ── checkVersion ─────────────────────────────────────────────────────

        given("checkVersion 호출 시") {
            `when`("클라이언트 버전이 최소 강제 버전보다 낮으면") {
                then("updateRequired=true, updateAvailable=true를 반환한다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns listOf(
                        version("1.0.0", forceUpdate = true),
                        version("1.1.0"),
                    )

                    val result = sut.checkVersion(Platform.IOS, "0.9.0")

                    result.updateRequired shouldBe true
                    result.updateAvailable shouldBe true
                    result.latestVersion shouldBe "1.1.0"
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
                then("updateRequired=false, updateAvailable=false를 반환한다") {
                    every { appVersionRepository.findAllByPlatform(Platform.IOS) } returns listOf(
                        version("1.0.0", forceUpdate = true),
                        version("1.1.0"),
                    )

                    val result = sut.checkVersion(Platform.IOS, "1.1.0")

                    result.updateRequired shouldBe false
                    result.updateAvailable shouldBe false
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
        }

        // ── addVersion ───────────────────────────────────────────────────────

        given("addVersion 호출 시") {
            val dto = AppVersionDto(
                platform = Platform.IOS,
                version = "1.0.0",
                forceUpdate = false,
                downloadUrl = "https://apps.apple.com/",
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
