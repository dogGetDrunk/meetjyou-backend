package com.dogGetDrunk.meetjyou.user.dto;

import com.dogGetDrunk.meetjyou.user.AuthProvider;
import lombok.Getter;

@Getter
public class RegistrationRequestDto {

    private String email;
    private String nickname;
    private int gender;
    private int age;
    private String bio;
    private AuthProvider authProvider;
}
