package com.dogGetDrunk.meetjyou.common.exception.business;

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode;

public class InvalidAuthorizationHeaderException extends CustomJwtException {

    private String value;

    public InvalidAuthorizationHeaderException(String value) {
        this(value, ErrorCode.INVALID_AUTHORIZATION_HEADER);
    }

    public InvalidAuthorizationHeaderException(String value, ErrorCode errorCode) {
        super(value, errorCode);
        this.value = value;
    }
}
