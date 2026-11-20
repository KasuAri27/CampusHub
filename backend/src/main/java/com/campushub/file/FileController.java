package com.campushub.file;

import com.campushub.common.ApiResponse;
import com.campushub.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 图片上传（API 契约第 11 节）。
 * <p>安全边界：上传目录经 {@code /uploads/**} 以站点同源公开访问，所以落盘的内容必须保证
 * 浏览器只会把它当图片渲染，不能出现 html、svg 等可在站点域名下执行脚本的类型（存储型 XSS）。
 * 因此只放行 jpg/jpeg/png/webp：扩展名与客户端声明的 Content-Type 必须同时匹配，
 * 磁盘文件名完全由服务端生成，客户端文件名只用于取扩展名，不参与路径拼接（同时杜绝路径穿越）。
 * 响应头 {@code X-Content-Type-Options: nosniff} 由 Spring Security 默认头加在静态资源响应上，
 * 由集成测试 {@code FileUploadIntegrationTest} 持续验证。
 */
@Slf4j
@RestController
@RequestMapping("/v1/files")
public class FileController {

    /** 允许的扩展名与其唯一对应的 Content-Type；jpg 与 jpeg 是同一种格式的两种常见写法。 */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private final File uploadsDir;

    /** 生产使用：图片保存在项目根目录下的 uploads 文件夹，与 WebConfig 的静态资源映射指向同一目录。 */
    public FileController() {
        this(defaultUploadsDir());
    }

    /** 测试使用：指定落盘目录，避免单测往项目目录写文件。 */
    FileController(File uploadsDir) {
        this.uploadsDir = uploadsDir;
    }

    private static File defaultUploadsDir() {
        File userDir = new File(System.getProperty("user.dir"));
        return userDir.getName().equals("backend")
                ? new File(userDir.getParentFile(), "uploads")
                : new File(userDir, "uploads");
    }

    /**
     * 上传一张图片并返回可访问的相对地址。
     * 校验失败一律 400（文件名为空、无扩展名、扩展名不在白名单、Content-Type 与扩展名不符）；
     * 磁盘写入失败只记日志并返回固定文案，不把异常原文（可能含服务器路径）返回给客户端。
     */
    @PostMapping("/upload")
    public ApiResponse<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }
        String extension = extensionOf(file.getOriginalFilename());
        String expectedType = ALLOWED_TYPES.get(extension);
        if (expectedType == null) {
            throw new BusinessException(400, "仅支持 jpg、jpeg、png、webp 格式的图片");
        }
        // Content-Type 由客户端声明，不能单独作为依据；这里要求它与扩展名对应的类型一致。
        String declaredType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!expectedType.equals(declaredType)) {
            throw new BusinessException(400, "文件类型与扩展名不符");
        }

        // 文件名由服务端随机生成，扩展名取自上面已通过白名单的小写值。
        String newFileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            if (!uploadsDir.exists() && !uploadsDir.mkdirs() && !uploadsDir.exists()) {
                throw new IOException("无法创建上传目录");
            }
            file.transferTo(new File(uploadsDir, newFileName));
        } catch (IOException e) {
            log.error("文件上传失败，保存目录={}", uploadsDir, e);
            throw new BusinessException(500, "文件上传失败，请稍后重试");
        }

        return ApiResponse.success(Map.of("url", "/api/uploads/" + newFileName));
    }

    /**
     * 取客户端文件名中最后一个点之后的部分并转小写；没有文件名、没有点或点在末尾都返回空串（随后被白名单拒绝）。
     * 只用于判断类型，调用方不得把原文件名拼进磁盘路径。
     */
    private static String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
