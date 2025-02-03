package com.dogGetDrunk.meetjyou.user;

import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateEmailException;
import com.dogGetDrunk.meetjyou.common.exception.business.EmailNotFoundException;
import com.dogGetDrunk.meetjyou.jwt.JwtManager;
import com.dogGetDrunk.meetjyou.preference.Etc;
import com.dogGetDrunk.meetjyou.preference.Personality;
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository;
import com.dogGetDrunk.meetjyou.preference.TravelStyle;
import com.dogGetDrunk.meetjyou.preference.UserPreference;
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository;
import com.dogGetDrunk.meetjyou.user.dto.LoginRequestDto;
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequestDto;
import com.dogGetDrunk.meetjyou.user.dto.TokenResponseDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PreferenceRepository preferenceRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final JwtManager jwtManager;

    @Transactional
    public TokenResponseDto createUser(RegistrationRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        User createdUser = userRepository.save(User.builder()
                .email(request.getEmail())
                .nickname(request.getNickname())
                .bio(request.getBio())
                .authProvider(request.getAuthProvider())
                .build());

        userPreferenceRepository.save(new UserPreference(
                createdUser,
                preferenceRepository.findByName(request.getGender().name())
        ));
        userPreferenceRepository.save(new UserPreference(
                createdUser,
                preferenceRepository.findByName(request.getAge().name())
        ));

        for (Personality p : request.getPersonality()) {
            userPreferenceRepository.save(new UserPreference(
                    createdUser,
                    preferenceRepository.findByName(p.name())
            ));
        }

        for (TravelStyle t : request.getTravelStyle()) {
            userPreferenceRepository.save(new UserPreference(
                    createdUser,
                    preferenceRepository.findByName(t.name())
            ));
        }

        userPreferenceRepository.save(new UserPreference(
                createdUser,
                preferenceRepository.findByName(request.getDiet().name())
        ));

        for (Etc e : request.getEtc()) {
            userPreferenceRepository.save(new UserPreference(
                    createdUser,
                    preferenceRepository.findByName(e.name())
            ));
        }

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


    public TokenResponseDto refreshToken(String refreshToken, String email) {
        jwtManager.validateToken(refreshToken, email);

        String newAccessToken = jwtManager.generateAccessToken(email);
        String newRefreshToken = jwtManager.generateRefreshToken(email);

        return TokenResponseDto.builder()
                .email(email)
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }
}
