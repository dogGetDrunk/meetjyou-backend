package com.dogGetDrunk.meetjyou.user;

import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateEmailException;
import com.dogGetDrunk.meetjyou.common.exception.business.EmailNotFoundException;
import com.dogGetDrunk.meetjyou.jwt.JwtManager;
import com.dogGetDrunk.meetjyou.user.dto.LoginRequestDto;
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequestDto;
import com.dogGetDrunk.meetjyou.user.dto.TokenResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JwtManager jwtManager;

    public TokenResponseDto createUser(RegistrationRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        User createdUser = userRepository.save(User.builder()
                .email(request.getEmail())
                .nickname(request.getNickname())
                .gender(request.getGender())
                .age(request.getAge())
                .bio(request.getBio())
                .authProvider(request.getAuthProvider())
                .build());

        String accessToken = jwtManager.generateAccessToken(request.getEmail());
        String refreshToken = jwtManager.generateRefreshToken(request.getEmail());

        return TokenResponseDto.builder()
                .email(createdUser.getEmail())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }


    public TokenResponseDto login(LoginRequestDto loginRequestDto) {
        String email = loginRequestDto.getEmail();

        if (!userRepository.existsByEmail(email)) {
            throw new EmailNotFoundException(email);
        }

        String accessToken = jwtManager.generateAccessToken(email);
        String refreshToken = jwtManager.generateRefreshToken(email);

        return TokenResponseDto.builder()
                .email(email)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }


    public boolean isDuplicateNickname(String nickname) {
        return userRepository.existsByNickname(nickname);
    }
}
