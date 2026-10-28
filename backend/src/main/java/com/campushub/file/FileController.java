package com.campushub.file;

import com.campushub.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/files")
public class FileController {

    // 图片保存在项目根目录下的 uploads 文件夹中
    private static final String UPLOAD_DIR;

    static {
        File userDir = new File(System.getProperty("user.dir"));
        File uploadsDir = userDir.getName().equals("backend")
                ? new File(userDir.getParentFile(), "uploads")
                : new File(userDir, "uploads");

        // 确保目录路径以系统分隔符结尾
        UPLOAD_DIR = uploadsDir.getAbsolutePath() + File.separator;
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件不能为空");
        }

        try {
            // 确保目录存在
            File dir = new File(UPLOAD_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 生成唯一的文件名，防止重名覆盖
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String newFileName = UUID.randomUUID().toString().replace("-", "") + extension;

            // 保存文件到本地
            File dest = new File(UPLOAD_DIR + newFileName);
            file.transferTo(dest);

            // 拼接可供前端访问的 URL（配合第三步的静态资源映射）
            String fileUrl = "/api/uploads/" + newFileName;

            // 返回前端需要的格式
            Map<String, String> data = new HashMap<>();
            data.put("url", fileUrl);
            return ApiResponse.success(data);

        } catch (IOException e) {
            e.printStackTrace();
            return ApiResponse.error(500, "文件上传失败：" + e.getMessage());
        }
    }
}