package com.dogGetDrunk.meetjyou.user.dto;

import com.dogGetDrunk.meetjyou.preference.Age;
import com.dogGetDrunk.meetjyou.preference.Diet;
import com.dogGetDrunk.meetjyou.preference.Etc;
import com.dogGetDrunk.meetjyou.preference.Gender;
import com.dogGetDrunk.meetjyou.preference.Personality;
import com.dogGetDrunk.meetjyou.preference.TravelStyle;
import com.dogGetDrunk.meetjyou.user.AuthProvider;
import lombok.Getter;

import java.util.List;

@Getter
public class RegistrationRequestDto {

    private String email;
    private String nickname;
    private String bio;
    private Gender gender;
    private Age age;
    private List<Personality> personality;
    private List<TravelStyle> travelStyle;
    private Diet diet;
    private List<Etc> etc;
    private AuthProvider authProvider;
}
