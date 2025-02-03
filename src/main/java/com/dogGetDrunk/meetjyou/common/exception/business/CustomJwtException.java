package com.dogGetDrunk.meetjyou.common.exception.business;

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class CustomJwtException extends BusinessException {

    private String value;

    public CustomJwtException(String value) {
        this(value, ErrorCode.TOKEN_COMMON);
    }

    public CustomJwtException(String value, ErrorCode errorCode) {
        super(value, errorCode);
        this.value = value;
    }
}

