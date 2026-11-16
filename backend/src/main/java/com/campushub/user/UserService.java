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
/**
 * 用户与认证领域服务。
 * 负责手机号 AES 加解密落库、BCrypt 密码校验、Spring Security 登录集成及信息脱敏展示。
 */
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final BcryptUtil bcryptUtil;
    private final AesUtil aesUtil;
    private final JwtUtil jwtUtil;
    private final VerificationCodeService verificationCodeService;

    /**
     * 学号注册。前置条件：已向该手机号、该学号申请过 REGISTER 验证码。
     * 学号、昵称必填，学院可选；角色固定为普通用户，校园邮箱由学号生成。
     * 整个方法在一个事务内：后续任何校验失败都会连同“验证码已使用”一起回滚，验证码可重试。
     */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        NicknameRule.check(request.getNickname());
        verificationCodeService.verifyCode(request.getPhone(), request.getCode(), "REGISTER", request.getStudentId());

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
        user.setRole(User.Role.user);
        user.setNickname(request.getNickname());
        user.setCollege(blankToNull(request.getCollege()));
        user.setCreditScore(100);
        user.setStatus(1);
        userRepository.save(user);

        return responseFor(user);
    }

    /** 密码登录：账号可以是学号，也可以是 11 位手机号（按确定性密文查找）。 */
    public LoginResponse loginWithPassword(LoginRequest request) {
        Optional<User> userOpt;
        if (request.getAccount().matches("^1[3-9]\\d{9}$")) {
            String encryptedPhone = aesUtil.encrypt(request.getAccount());
            userOpt = userRepository.findByPhone(encryptedPhone);
        } else {
            userOpt = userRepository.findByStudentId(request.getAccount());
        }

        User user = userOpt.orElseThrow(() -> new BusinessException("账号不存在"));
        if (!bcryptUtil.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        return responseFor(user);
    }

    /** 手机号 + 邮箱验证码登录，只面向已注册账号，不会自动注册。 */
    @Transactional
    public LoginResponse loginWithSms(SmsLoginRequest request) {
        verificationCodeService.verifyCode(request.getPhone(), request.getCode(), "LOGIN");

        String encryptedPhone = aesUtil.encrypt(request.getPhone());
        Optional<User> userOpt = userRepository.findByPhone(encryptedPhone);
        // 手机验证码登录只用于已有校内账号，清理无法产生有效JWT的空学号自动注册分支。
        User user = userOpt.orElseThrow(() -> new BusinessException(404, "账号未注册，请先完成校内注册"));

        return responseFor(user);
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
                user.getCollege(),
                user.getCreditScore(),
                user.getRole().name(),
                user.getStatus() != 1
        );
    }

    /**
     * 修改个人资料，只能改昵称、头像、学院。字段为 null 表示不修改；学院传空字符串表示清空。
     * 昵称按 {@link NicknameRule} 校验后才落库。
     */
    @Transactional
    public void updateProfile(Long userId, UserProfileDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (dto.getNickname() != null) {
            NicknameRule.check(dto.getNickname());
            user.setNickname(dto.getNickname());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getCollege() != null) {
            user.setCollege(blankToNull(dto.getCollege()));
        }
        // 学号、姓名、邮箱注册后不可更改：请求体里即使带了这些字段也不采纳。

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

    /**
     * 封禁/解封切换：账号状态的唯一写入口，供管理端调用（K1：admin 不得直接改 User.status）。
     * 通过 {@link UserRepository#findLockedById} 取悲观写锁后再读后写，
     * 两个管理员同时点击同一账号时第二个请求会看到第一个的结果，不会各自读到旧值后互相覆盖。
     * 调用方须自带事务（admin 的事务会被本方法加入）；管理员身份校验由调用方负责，本方法不判断操作者。
     * 不改动信用分和订单；封禁后的受限登录规则见 {@link #responseFor}。
     *
     * @return 切换后的状态：1=正常，0=封禁
     */
    @Transactional
    public int toggleStatus(Long userId) {
        User target = userRepository.findLockedById(userId)
                .orElseThrow(() -> new BusinessException(404, "目标用户不存在"));
        target.setStatus(target.getStatus() != null && target.getStatus() == 1 ? 0 : 1);
        return target.getStatus();
    }

    @Override
    public UserDetails loadUserByUsername(String studentId) throws UsernameNotFoundException {
        User user = userRepository.findByStudentId(studentId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        return new org.springframework.security.core.userdetails.User(
                user.getStudentId(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getStatus() == 1 ? "ROLE_USER" : "ROLE_RESTRICTED"))
        );
    }

    /**
     * 签发 JWT 并组装注册、登录共用的响应（token、role、user）。
     * 封禁用户也能拿到 token 进入受限账户（restricted=true）；具体允许的还款/申诉路径由过滤链控制。
     */
    private LoginResponse responseFor(User user) {
        String token = jwtUtil.generateToken(user.getStudentId(), user.getId());
        LoginResponse.UserInfo info = new LoginResponse.UserInfo(
                user.getId(),
                user.getStudentId(),
                user.getNickname(),
                user.getAvatar(),
                user.getCollege(),
                user.getCreditScore(),
                user.getStatus() != 1
        );
        return new LoginResponse(token, user.getRole().name(), info);
    }

    /** 可选文本字段的统一归一：null 或纯空白一律存为 null，避免库里同时出现 NULL 和空串两种“未填写”。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
