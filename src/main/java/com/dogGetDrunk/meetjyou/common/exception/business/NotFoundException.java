package com.dogGetDrunk.meetjyou.common.exception.business;

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class NotFoundException extends BusinessException {

    private String value;

    public NotFoundException(String value) {
        this(value, ErrorCode.NOT_FOUND);
        this.value = value;
    }

    public NotFoundException(String value, ErrorCode errorCode) {
        super(value, errorCode);
        this.value = value;
    }
}
