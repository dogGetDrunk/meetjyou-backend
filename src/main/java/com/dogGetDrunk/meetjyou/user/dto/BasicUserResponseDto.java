package com.dogGetDrunk.meetjyou.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class BasicUserResponseDto {
    private String nickname;
    private String bio;
    private String gender;
    private String age;
    private List<String> personalities;
    private List<String> travelStyles;
    private String diet;
    private List<String> etc;
}
