package com.dogGetDrunk.meetjyou.user.dto;

import com.dogGetDrunk.meetjyou.preference.Age;
import com.dogGetDrunk.meetjyou.preference.Diet;
import com.dogGetDrunk.meetjyou.preference.Etc;
import com.dogGetDrunk.meetjyou.preference.Gender;
import com.dogGetDrunk.meetjyou.preference.Personality;
import com.dogGetDrunk.meetjyou.preference.TravelStyle;
import lombok.Getter;

import java.util.List;

@Getter
public class UserUpdateRequestDto {
    private String nickname;
    private String bio;
    private Gender gender;
    private Age age;
    private List<Personality> personalities;
    private List<TravelStyle> travelStyles;
    private Diet diet;
    private List<Etc> etc;
}
