package com.dogGetDrunk.meetjyou.cloud.oracle.dto

import com.dogGetDrunk.meetjyou.image.ImageOperation
import com.dogGetDrunk.meetjyou.image.ImageTarget
import java.util.UUID

data class ParRequest(
    val uuid: UUID,
    val target: ImageTarget,
    val operation: ImageOperation
)
