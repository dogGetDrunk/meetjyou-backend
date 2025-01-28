package com.dogGetDrunk.meetjyou.user.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class TokenResponseDto {

    private String email;
    private String accessToken;
    private String refreshToken;
}
