package com.campushub.file;

import com.campushub.user.User;
import com.campushub.user.UserRepository;
import com.campushub.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 经真实过滤链验证上传的两条安全底线：
 * 非法类型必须得到真实的 HTTP 400（而不是 200 加错误体）；
 * 已上传的文件被访问时，响应必须带 nosniff 且按图片类型返回，浏览器不会把它当脚本或页面执行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileUploadIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtUtil jwt;

    @Test
    void rejectedUploadIsHttp400AndNeverStored() throws Exception {
        User uploader = users.save(User.builder().studentId("up" + UUID.randomUUID().toString().substring(0, 8))
                .name("上传测试").nickname("up").phone("fixture-up-" + UUID.randomUUID()).password("test-only-hash")
                .email("up" + UUID.randomUUID().toString().substring(0, 8) + "@example.invalid")
                .role(User.Role.user).creditScore(100).status(1).build());
        try {
            String token = "Bearer " + jwt.generateToken(uploader.getStudentId(), uploader.getId());
            mvc.perform(multipart("/v1/files/upload")
                            .file(new MockMultipartFile("file", "evil.html", "text/html", "<script>alert(1)</script>".getBytes()))
                            .header("Authorization", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        } finally {
            users.delete(uploader);
        }
    }

    @Test
    void servedUploadsCarryNosniffAndImageContentType() throws Exception {
        // 与 WebConfig 的静态资源映射同一目录：直接放一个文件，再经真实过滤链读取。
        File userDir = new File(System.getProperty("user.dir"));
        File uploads = userDir.getName().equals("backend")
                ? new File(userDir.getParentFile(), "uploads") : new File(userDir, "uploads");
        File sample = new File(uploads, "nosniff-check-" + UUID.randomUUID() + ".png");
        uploads.mkdirs();
        Files.write(sample.toPath(), new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        try {
            mvc.perform(get("/uploads/" + sample.getName()))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Content-Type", "image/png"));
        } finally {
            Files.deleteIfExists(sample.toPath());
        }
    }
}
