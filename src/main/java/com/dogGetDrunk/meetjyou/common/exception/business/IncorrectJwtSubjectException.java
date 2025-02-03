package com.dogGetDrunk.meetjyou.common.exception.business;

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode;

public class IncorrectJwtSubjectException extends CustomJwtException {

    private String value;

    public IncorrectJwtSubjectException(String value) {
        super(value, ErrorCode.INCORRECT_TOKEN_SUBJECT);
        this.value = value;
    }
}
