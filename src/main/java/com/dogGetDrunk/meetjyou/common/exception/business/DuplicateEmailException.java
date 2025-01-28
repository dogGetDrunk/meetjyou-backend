package com.dogGetDrunk.meetjyou.common.exception.business;

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class DuplicateEmailException extends DuplicateException {

    private String email;

    public DuplicateEmailException(String email) {
        super(email, ErrorCode.DUPLICATE_EMAIL);
    }
}
