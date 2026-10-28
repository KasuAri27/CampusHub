package com.campushub.user;

import com.campushub.common.BusinessException;
import com.campushub.util.AesUtil;
import com.campushub.util.BcryptUtil;
import com.campushub.util.JwtUtil;
import com.campushub.verification.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final BcryptUtil bcryptUtil;
    private final AesUtil aesUtil;
    private final JwtUtil jwtUtil;
    private final VerificationCodeService verificationCodeService;

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        verificationCodeService.verifyCode(request.getPhone(), request.getSmsCode(), "REGISTER");

        if (userRepository.existsByStudentId(request.getStudentId())) {
            throw new BusinessException("学号已注册");
        }

        String encryptedPhone = aesUtil.encrypt(request.getPhone());
        if (userRepository.existsByPhone(encryptedPhone)) {
            throw new BusinessException("手机号已注册");
        }

        User user = new User();
        user.setName(request.getName());
        user.setStudentId(request.getStudentId());
        // 使用学号构造官方邮箱
        user.setEmail(request.getStudentId() + "@smail.nju.edu.cn");
        user.setPhone(encryptedPhone);
        user.setPassword(bcryptUtil.encode(request.getPassword()));
        user.setRole(User.Role.requester);
        user.setNickname("User" + request.getStudentId().substring(0, 4));
        user.setCreditScore(100);
        user.setStatus(1);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getStudentId(), user.getId());
        return new LoginResponse(token, user.getId(), user.getStudentId(), user.getNickname(),
                user.getRole() != null ? user.getRole().name() : null);
    }

    public LoginResponse loginWithPassword(LoginRequest request) {
        Optional<User> userOpt;
        if (request.getAccount().matches("^1[3-9]\\d{9}$")) {
            String encryptedPhone = aesUtil.encrypt(request.getAccount());
            userOpt = userRepository.findByPhone(encryptedPhone);
        } else {
            userOpt = userRepository.findByStudentId(request.getAccount());
        }

        User user = userOpt.orElseThrow(() -> new BusinessException("账号不存在"));
        if (user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }
        if (!bcryptUtil.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        String token = jwtUtil.generateToken(user.getStudentId(), user.getId());
        return new LoginResponse(token, user.getId(), user.getStudentId(), user.getNickname(),
                user.getRole() != null ? user.getRole().name() : null);
    }

    @Transactional
    public LoginResponse loginWithSms(SmsLoginRequest request) {
        verificationCodeService.verifyCode(request.getPhone(), request.getCode(), "LOGIN");

        String encryptedPhone = aesUtil.encrypt(request.getPhone());
        Optional<User> userOpt = userRepository.findByPhone(encryptedPhone);
        User user;
        if (userOpt.isEmpty()) {
            // 自动注册新用户（无学号，邮箱暂用手机号占位）
            user = new User();
            user.setName("User" + request.getPhone().substring(7));
            user.setStudentId("");
            user.setEmail(request.getPhone() + "@smail.nju.edu.cn"); // 占位，后续可更新
            user.setPhone(encryptedPhone);
            user.setPassword(bcryptUtil.encode(""));
            user.setRole(User.Role.requester);
            user.setNickname("User" + request.getPhone().substring(7));
            user.setCreditScore(100);
            user.setStatus(1);
            userRepository.save(user);
        } else {
            user = userOpt.get();
            if (user.getStatus() != 1) {
                throw new BusinessException("账号已被禁用");
            }
        }

        String token = jwtUtil.generateToken(user.getStudentId(), user.getId());
        LoginResponse response = new LoginResponse(token, user.getId(), user.getStudentId(),
                user.getNickname(), user.getRole() != null ? user.getRole().name() : null);
        response.setNeedBindStudentId(user.getStudentId().isEmpty());
        return response;
    }

    public UserProfileDTO getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        String plainPhone;
        try {
            plainPhone = aesUtil.decrypt(user.getPhone());
        } catch (RuntimeException e) {
            plainPhone = "***";
        }
        return new UserProfileDTO(
                user.getId(),
                user.getStudentId(),
                plainPhone,
                user.getEmail(),
                user.getNickname(),
                user.getAvatar(),
                user.getCreditScore(),
                user.getRole() != null ? user.getRole().name() : null
        );
    }

    @Transactional
    public void updateProfile(Long userId, UserProfileDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }

        // 如果更新了学号，自动同步更新邮箱
        if (dto.getStudentId() != null && !dto.getStudentId().isEmpty()) {
            // 检查新学号是否已被其他用户占用
            if (!dto.getStudentId().equals(user.getStudentId())) {
                if (userRepository.existsByStudentId(dto.getStudentId())) {
                    throw new BusinessException("学号已被其他用户绑定");
                }
                user.setStudentId(dto.getStudentId());
                // 自动更新邮箱为新的学号邮箱
                user.setEmail(dto.getStudentId() + "@smail.nju.edu.cn");
            }
        }

        userRepository.save(user);
    }

    @Transactional
    public void updateUserRole(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        User.Role role = User.Role.valueOf(roleName);
        user.setRole(role);
        userRepository.save(user);
    }

    @Transactional
    public void updatePassword(Long userId, PasswordUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (!bcryptUtil.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("当前密码错误");
        }
        user.setPassword(bcryptUtil.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String studentId) throws UsernameNotFoundException {
        User user = userRepository.findByStudentId(studentId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        return new org.springframework.security.core.userdetails.User(
                user.getStudentId(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}