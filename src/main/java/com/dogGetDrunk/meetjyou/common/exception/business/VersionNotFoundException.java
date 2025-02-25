package com.dogGetDrunk.meetjyou.common.exception.business;

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode;

public class VersionNotFoundException extends NotFoundException {

    private String version;

    public VersionNotFoundException(String version) {
        super(version, ErrorCode.VERSION_NOT_FOUND);
    }
}
