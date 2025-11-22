package com.dogGetDrunk.meetjyou.image.cloud.oracle.dto

import com.dogGetDrunk.meetjyou.image.ImageOperation
import com.dogGetDrunk.meetjyou.image.ImageTarget
import java.util.UUID

data class BulkParRequest(
    val uuid: List<UUID>,
    val target: ImageTarget,
    val operation: ImageOperation
)
