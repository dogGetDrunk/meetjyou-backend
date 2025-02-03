package com.dogGetDrunk.meetjyou.common.exception.business;

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode;

public class UserNotFoundException extends NotFoundException {

    private String userId;

    public UserNotFoundException(Long userId) {
        super(Long.toString(userId), ErrorCode.USER_NOT_FOUND);
    }

    public UserNotFoundException(String userId) {
        super(userId, ErrorCode.USER_NOT_FOUND);
    }
}
