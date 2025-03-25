package com.dogGetDrunk.meetjyou.common.exception

data class ErrorResponse(
    val status: Int,
    val message: String,
    val values: List<String> = emptyList()
) {
    constructor(status: Int, errorCode: ErrorCode) : this(
        status,
        errorCode.message
    )

    constructor(status: Int, errorCode: ErrorCode, value: String) : this(
        status,
        errorCode.message,
        listOf(value)
    )

    constructor(status: Int, errorCode: ErrorCode, values: List<String>) : this(
        status,
        errorCode.message,
        values
    )
}
