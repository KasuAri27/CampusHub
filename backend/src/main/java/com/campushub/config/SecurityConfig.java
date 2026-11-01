package com.campushub.config;

import com.campushub.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF（使用 Lambda 表达式，避免弃用警告）
                .csrf(csrf -> csrf.disable())
                // 设置无状态会话
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 明确认证契约，不能让“任意4xx”掩盖过滤链或参数错误。
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"请先登录\"}");
                }))
                // 请求授权
                .authorizeHttpRequests(auth -> auth
                        // 游客可读：任务列表与详情、分类、上传的图片与头像
                        .requestMatchers("/v1/tasks/mine/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/v1/tasks/**", "/v1/categories", "/uploads/**").permitAll()
                        // 账号相关：注册、密码登录、短信登录、验证码
                        .requestMatchers("/v1/auth/register", "/v1/auth/login/**", "/v1/verification/**").permitAll()
                        // 支付回调只放行精确的 POST 路径，真实性由官方 SDK 的渠道验签保证。
                        .requestMatchers(HttpMethod.POST, "/v1/payments/notify/wechat", "/v1/payments/notify/alipay").permitAll()
                        // WebSocket 握手（浏览器无法自定义请求头，身份在 handler 内校验）
                        .requestMatchers("/ws/**", "/error").permitAll()
                        // 封禁后的受限身份仅保留本人订单、付款、投诉和账号安全入口。
                        .requestMatchers(HttpMethod.GET, "/v1/orders/**", "/v1/payments/**", "/v1/complaints/**", "/v1/auth/profile").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/payments/orders/*", "/v1/complaints").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/v1/auth/password").authenticated()
                        .anyRequest().hasAuthority("ROLE_USER")
                )
                // 添加 JWT 过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
