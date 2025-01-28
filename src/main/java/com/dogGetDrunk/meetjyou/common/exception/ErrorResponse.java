package com.dogGetDrunk.meetjyou.common.exception;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ErrorResponse {

    private int status;
    private String message;
    private List<String> values = new ArrayList<>();

    public ErrorResponse(int status, ErrorCode errorCode) {
        this.status = status;
        this.message = errorCode.getMessage();
    }

    public ErrorResponse(int status, ErrorCode errorCode, String value) {
        this(status, errorCode);
        this.values = List.of(value);
    }

    public ErrorResponse(int status, ErrorCode errorCode, List<String> values) {
        this(status, errorCode);
        this.values = values;
    }
}
