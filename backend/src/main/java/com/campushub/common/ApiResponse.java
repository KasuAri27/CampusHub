package com.campushub.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一 API 响应包装类. 所有 Controller 返回值都通过此类包装，确保前端收到统一 JSON 结构.
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String error;

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "success", null, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data, null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null, null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }

    public static <T> ApiResponse<T> error(int code, String message, String error) {
        return new ApiResponse<>(code, message, null, error);
    }
}