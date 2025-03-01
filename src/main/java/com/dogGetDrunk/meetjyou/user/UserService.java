package com.dogGetDrunk.meetjyou.user;

import com.dogGetDrunk.meetjyou.common.exception.business.DuplicateEmailException;
import com.dogGetDrunk.meetjyou.common.exception.business.UserNotFoundException;
import com.dogGetDrunk.meetjyou.jwt.JwtManager;
import com.dogGetDrunk.meetjyou.preference.Preference;
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository;
import com.dogGetDrunk.meetjyou.preference.UserPreference;
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository;
import com.dogGetDrunk.meetjyou.user.dto.LoginRequestDto;
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequestDto;
import com.dogGetDrunk.meetjyou.user.dto.TokenResponseDto;
import com.dogGetDrunk.meetjyou.user.dto.UserResponseDto;
import com.dogGetDrunk.meetjyou.user.dto.UserUpdateRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

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
                .birthDate(request.getBirthDate())
                .authProvider(request.getAuthProvider())
                .build());

        saveUserPreference(createdUser, request.getGender().name());
        saveUserPreference(createdUser, request.getAge().name());

        request.getPersonalities().forEach(p -> saveUserPreference(createdUser, p.name()));
        request.getTravelStyles().forEach(t -> saveUserPreference(createdUser, t.name()));
        saveUserPreference(createdUser, request.getDiet().name());
        request.getEtc().forEach(e -> saveUserPreference(createdUser, e.name()));

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

    @Transactional
    public UserResponseDto updateUser(Long userId, UserUpdateRequestDto requestDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setNickname(requestDto.getNickname());
        user.setBio(requestDto.getBio());

        updateUserPreference(user, requestDto.getGender().name(), 0);
        updateUserPreference(user, requestDto.getAge().name(), 1);

        updateUserPreferences(user, requestDto.getPersonalities().stream().map(Enum::name).collect(Collectors.toList()), 2);
        updateUserPreferences(user, requestDto.getTravelStyles().stream().map(Enum::name).collect(Collectors.toList()), 3);
        updateUserPreference(user, requestDto.getDiet().name(), 4);
        updateUserPreferences(user, requestDto.getEtc().stream().map(Enum::name).collect(Collectors.toList()), 5);

        userRepository.save(user);
        log.info("유저 정보 수정 완료: id {}", user.getId());
        return getUserProfile(userId);
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String gender = getPreferenceName(user.getId(), 0);
        String age = getPreferenceName(user.getId(), 1);
        List<String> personalities = getPreferenceNames(user.getId(), 2);
        List<String> travelStyles = getPreferenceNames(user.getId(), 3);
        String diet = getPreferenceName(user.getId(), 4);
        List<String> etcPreferences = getPreferenceNames(user.getId(), 5);

        return new UserResponseDto(user.getNickname(), user.getBio(), gender, age, personalities, travelStyles, diet, etcPreferences);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> getAllUsersProfile() {
        List<User> users = userRepository.findAll();
        List<UserResponseDto> result = new LinkedList<>();

        for (User user : users) {
            String gender = getPreferenceName(user.getId(), 0);
            String age = getPreferenceName(user.getId(), 1);
            List<String> personalities = getPreferenceNames(user.getId(), 2);
            List<String> travelStyles = getPreferenceNames(user.getId(), 3);
            String diet = getPreferenceName(user.getId(), 4);
            List<String> etcPreferences = getPreferenceNames(user.getId(), 5);
            result.add(new UserResponseDto(user.getNickname(), user.getBio(), gender, age, personalities, travelStyles, diet, etcPreferences));
        }

        return result;
    }

    private void saveUserPreference(User user, String preferenceName) {
        Preference preference = preferenceRepository.findByName(preferenceName);
        if (preference != null) {
            userPreferenceRepository.save(new UserPreference(user, preference));
        }
    }

    private void updateUserPreferences(User user, List<String> preferenceNames, int type) {
        userPreferenceRepository.deleteByUserIdAndType(user.getId(), type);
        for (String preferenceName : preferenceNames) {
            Preference preference = preferenceRepository.findByName(preferenceName);
            if (preference != null) {
                userPreferenceRepository.save(new UserPreference(user, preference));
            }
        }
    }

    private void updateUserPreference(User user, String preferenceName, int type) {
        if (preferenceName == null) return;

        Preference preference = preferenceRepository.findByName(preferenceName);
        if (preference != null) {
            userPreferenceRepository.deleteByUserIdAndType(user.getId(), type);
            userPreferenceRepository.save(new UserPreference(user, preference));
        }
    }

    private String getPreferenceName(Long userId, int type) {
        return userPreferenceRepository.findPreferenceByUserIdAndType(userId, type)
                .map(Preference::getName)
                .orElse(null);
    }

    private List<String> getPreferenceNames(Long userId, int type) {
        return userPreferenceRepository.findPreferencesByUserIdAndType(userId, type)
                .stream().map(Preference::getName).collect(Collectors.toList());
    }
}
