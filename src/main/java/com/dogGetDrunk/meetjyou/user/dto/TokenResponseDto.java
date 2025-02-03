package com.dogGetDrunk.meetjyou.user.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class TokenResponseDto {

    private Long id;
    private String accessToken;
    private String refreshToken;
}
