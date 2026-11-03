package com.campushub.util;

/** 当前请求线程的用户上下文，利用 ThreadLocal 存储当前鉴权通过的用户 ID，并在过滤器生命周期结束时清理防泄漏。 */
public class UserContext {
    private static final ThreadLocal<Long> currentUserId = new ThreadLocal<>();

    public static void setCurrentUserId(Long userId) {
        currentUserId.set(userId);
    }

    public static Long getCurrentUserId() {
        return currentUserId.get();
    }

    public static void clear() {
        currentUserId.remove();
    }
}