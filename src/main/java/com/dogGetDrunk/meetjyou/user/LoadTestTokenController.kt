package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.config.RestControllerV1
import com.dogGetDrunk.meetjyou.user.dto.LoadTestTokenResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

@RestControllerV1
@RequestMapping("/internal")
class LoadTestTokenController(
    private val loadTestTokenService: LoadTestTokenService,
) {
    @PostMapping("/load-test-token")
    fun issue(
        @RequestHeader("X-Load-Test-Secret", required = false) secret: String?,
    ): LoadTestTokenResponse {
        return loadTestTokenService.issueToken(secret)
    }
}
