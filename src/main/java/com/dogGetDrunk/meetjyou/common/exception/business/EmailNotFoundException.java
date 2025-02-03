package com.dogGetDrunk.meetjyou.common.exception.business;

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode;

public class EmailNotFoundException extends NotFoundException {

    private String email;

    public EmailNotFoundException(String email) {
        super(email, ErrorCode.EMAIL_NOT_FOUND);
    }
}
