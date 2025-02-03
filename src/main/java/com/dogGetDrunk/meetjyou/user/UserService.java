package com.dogGetDrunk.meetjyou.user;

import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateEmailException;
import com.dogGetDrunk.meetjyou.common.exception.business.UserNotFoundException;
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

        String accessToken = jwtManager.generateAccessToken(createdUser.getId());
        String refreshToken = jwtManager.generateRefreshToken(createdUser.getId());

        return TokenResponseDto.builder()
                .id(createdUser.getId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }


    public TokenResponseDto login(LoginRequestDto loginRequestDto) {
        Long userId = loginRequestDto.getUserId();

        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        String accessToken = jwtManager.generateAccessToken(userId);
        String refreshToken = jwtManager.generateRefreshToken(userId);

        return TokenResponseDto.builder()
                .id(userId)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }


    public boolean isDuplicateNickname(String nickname) {
        return userRepository.existsByNickname(nickname);
    }


    public TokenResponseDto refreshToken(String refreshToken, Long userId) {
        jwtManager.validateToken(refreshToken, userId);

        String newAccessToken = jwtManager.generateAccessToken(userId);
        String newRefreshToken = jwtManager.generateRefreshToken(userId);

        return TokenResponseDto.builder()
                .id(userId)
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Transactional
    public void withdrawUser(Long userId, String accessToken) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        jwtManager.validateToken(accessToken, userId);

        log.info("유저 탈퇴 시작 (user id: {})", userId);
        userRepository.deleteById(userId);
        log.info("유저 탈퇴 성공 (user id: {})", userId);
    }
}
