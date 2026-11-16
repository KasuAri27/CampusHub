package com.campushub.user;

import com.campushub.common.BusinessException;

import java.nio.charset.StandardCharsets;

/**
 * 昵称限长规则：最长 10 个汉字或 20 个英文字符（汉字计 2，英文数字计 1）。
 * <p>
 * 算法与库表约束 {@code chk_users_nickname_length} 完全一致：
 * UTF-8 字节数 + 字符数 ≤ 40。汉字 3 字节，合计 4；英文 1 字节，合计 2，
 * 所以上限 40 恰好是“10 个汉字或 20 个英文字符”。
 * 在应用层先行校验，是为了给出明确的 400 提示；否则超长昵称会撞上数据库约束，
 * 被统一异常处理当成 409“记录冲突”。
 * </p>
 */
final class NicknameRule {

    /** 与数据库 CHECK 约束相同的权重上限。 */
    static final int MAX_WEIGHT = 40;

    private NicknameRule() {
    }

    /**
     * 校验昵称非空白且不超长。
     *
     * @throws BusinessException 400，昵称为空或超出长度限制
     */
    static void check(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new BusinessException(400, "昵称不能为空");
        }
        // 字符数按码点计，与 MySQL utf8mb4 下的 CHAR_LENGTH 一致（表情等补充字符算 1 个字符）。
        int weight = nickname.getBytes(StandardCharsets.UTF_8).length
                + nickname.codePointCount(0, nickname.length());
        if (weight > MAX_WEIGHT) {
            throw new BusinessException(400, "昵称最长10个汉字或20个英文字符");
        }
    }
}
