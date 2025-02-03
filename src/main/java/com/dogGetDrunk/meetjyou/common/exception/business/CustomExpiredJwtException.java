package com.dogGetDrunk.meetjyou.common.exception.business;

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode;

public class CustomExpiredJwtException extends CustomJwtException {

    private String value;

    public CustomExpiredJwtException(String value) {
        super(value, ErrorCode.EXPIRED_TOKEN);
    }
}
