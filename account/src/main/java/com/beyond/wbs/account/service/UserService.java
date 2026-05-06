package com.beyond.wbs.account.service;

import com.beyond.wbs.account.auth.PermissionCacheService;
import com.beyond.wbs.account.domain.User;
import com.beyond.wbs.account.dtos.MyInfoUpdateReqDto;
import com.beyond.wbs.account.dtos.UserDetailResDto;
import com.beyond.wbs.account.dtos.UserLoginReqDto;
import com.beyond.wbs.account.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCacheService permissionCacheService;

    @Autowired
    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       PermissionCacheService permissionCacheService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionCacheService = permissionCacheService;
    }

    public User login(UserLoginReqDto dto) {
        User user = userRepository.findByLoginIdWithRole(dto.getLoginId())
                .orElseThrow(() -> new IllegalArgumentException("로그인 ID 또는 비밀번호가 일치하지 않습니다."));

        if (!user.isActive()) {
            throw new IllegalArgumentException("비활성화된 계정입니다.");
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("로그인 ID 또는 비밀번호가 일치하지 않습니다.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public UserDetailResDto findById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("해당 id가 없습니다"));
        List<String> permissions = permissionCacheService.getPermissions(id);
        return UserDetailResDto.fromEntity(user, permissions);
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        user.changePassword(passwordEncoder.encode(newPassword));
    }

    public void updateMyInfo(UUID userId, MyInfoUpdateReqDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        user.updateMyInfo(dto.getEmail(), dto.getPhone());
    }
}
