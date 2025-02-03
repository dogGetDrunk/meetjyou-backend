package com.dogGetDrunk.meetjyou.common.exception.business;

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class DuplicateException extends BusinessException {

    private String value;

    public DuplicateException(String value) {
        this(value, ErrorCode.DUPLICATE);
        this.value = value;
    }

    public DuplicateException(String value, ErrorCode errorCode) {
        super(value, errorCode);
        this.value = value;
    }
}
