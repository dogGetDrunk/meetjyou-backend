package com.dogGetDrunk.meetjyou.cloud.oracle.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.util.UUID

data class BulkRequest(
    @field:NotEmpty
    @field:Size(max = 50)
    val uuid: List<UUID>
)
