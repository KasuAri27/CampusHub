package com.campushub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;


/**
 * Spring MVC 全局配置类.
 * <p>
 * 主要职责：配置 CORS（跨域资源共享）策略，
 *
 *
 * 允许前端开发服务器（如 Vite localhost:5173）跨域调用后端 API。
 * </p>
 * <p>
 * 原理：当浏览器发起跨域请求时，先发送一个 HTTP OPTIONS 预检请求，
 * 后端返回允许的来源、方法、Header 列表，
 * 浏览器验证通过后才发送真正的 GET/POST 请求。
 * </p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 注册 CORS 映射规则.
     * <p>
     * 针对所有 /v1/** 路径（即全部业务 API）：
     * <ul>
     *   <li>{@code allowedOriginPatterns("*")} — 允许任意来源访问（开发阶段），
     *       生产环境应限制为前端部署的域名</li>
     *   <li>{@code allowedMethods} — 允许的 HTTP 方法包括 GET、POST、PUT、DELETE、OPTIONS</li>
     *   <li>{@code allowedHeaders("*")} — 允许携带任意请求头（包括 Authorization Token）</li>
     *   <li>{@code allowCredentials(true)} — 允许携带 Cookie / Authorization 等凭据</li>
     * </ul>
     * </p>
     *
     * @param registry Spring MVC 提供的 CORS 注册中心，通过链式调用添加规则
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 1. 自动识别工作目录：如果当前在 backend 目录下，则退回上一级寻找同级的 uploads 文件夹
        java.io.File userDir = new java.io.File(System.getProperty("user.dir"));
        java.io.File uploadsDir = userDir.getName().equals("backend")
                ? new java.io.File(userDir.getParentFile(), "uploads")
                : new java.io.File(userDir, "uploads");

        // 2. 使用 toURI().toString() 完美解决 Windows/Linux/Mac 的盘符与斜杠兼容问题（生成标准统一的 file:/// 协议）
        String uploadPath = uploadsDir.toURI().toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath);
    }
}
