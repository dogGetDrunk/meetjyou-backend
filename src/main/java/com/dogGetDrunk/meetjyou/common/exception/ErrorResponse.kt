package com.dogGetDrunk.meetjyou.common.exception

/**
 * Built from an [ErrorCode], the status always comes from [ErrorCode.status]; only the
 * built-in Spring MVC exceptions, whose status Spring decides, use the primary constructor.
 */
data class ErrorResponse(
    val status: Int,
    val errorCode: String,
    val message: String,
    val values: List<String?> = emptyList()
) {
    constructor(errorCode: ErrorCode) : this(
        errorCode.status.value(),
        errorCode.name,
        errorCode.message
    )

    constructor(errorCode: ErrorCode, value: String?) : this(
        errorCode.status.value(),
        errorCode.name,
        errorCode.message,
        listOf(value)
    )

    constructor(errorCode: ErrorCode, values: List<String?>) : this(
        errorCode.status.value(),
        errorCode.name,
        errorCode.message,
        values
    )
}
