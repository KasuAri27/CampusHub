package com.campushub.file;

import com.campushub.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 上传白名单与落盘规则：直接断言文件是否落盘、落成什么名字，而不是只看返回值。 */
class FileControllerTest {

    @TempDir File dir;

    private MockMultipartFile upload(String filename, String contentType) {
        return new MockMultipartFile("file", filename, contentType, new byte[]{1, 2, 3});
    }

    private int rejectedWith(MockMultipartFile file) {
        FileController controller = new FileController(dir);
        BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> controller.uploadFile(file));
        // 被拒绝的上传不能在磁盘上留下任何文件。
        assertThat(dir.list()).isEmpty();
        return error.getCode();
    }

    @ParameterizedTest
    @CsvSource({"photo.png,image/png,png", "photo.jpg,image/jpeg,jpg", "photo.jpeg,image/jpeg,jpeg",
            "photo.webp,image/webp,webp", "PHOTO.PNG,image/png,png", "a.html.png,image/png,png", "头像.Jpg,IMAGE/JPEG,jpg"})
    void acceptsAllowedImagesAndRenamesOnServer(String filename, String contentType, String extension) throws Exception {
        Map<String, String> data = new FileController(dir).uploadFile(upload(filename, contentType)).getData();

        String url = data.get("url");
        assertThat(url).matches("/api/uploads/[0-9a-f]{32}\\." + extension);
        File saved = new File(dir, url.substring("/api/uploads/".length()));
        assertThat(Files.readAllBytes(saved.toPath())).containsExactly(1, 2, 3);
        assertThat(dir.list()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"page.html,text/html", "image.svg,image/svg+xml", "image.gif,image/gif", "run.exe,application/octet-stream",
            "shell.jsp,image/png"})
    void rejectsTypesOutsideWhitelist(String filename, String contentType) {
        assertThat(rejectedWith(upload(filename, contentType))).isEqualTo(400);
    }

    @Test
    void rejectsMissingOrEmptyExtension() {
        assertThat(rejectedWith(upload("noextension", "image/png"))).isEqualTo(400);
        assertThat(rejectedWith(upload("trailingdot.", "image/png"))).isEqualTo(400);
        assertThat(rejectedWith(upload("", "image/png"))).isEqualTo(400);
        assertThat(rejectedWith(upload(null, "image/png"))).isEqualTo(400);
    }

    @Test
    void rejectsContentTypeThatDoesNotMatchExtension() {
        // 扩展名合法但声明成 html：这是伪装上传，必须拒绝。
        assertThat(rejectedWith(upload("evil.png", "text/html"))).isEqualTo(400);
        assertThat(rejectedWith(upload("evil.png", "image/jpeg"))).isEqualTo(400);
        assertThat(rejectedWith(upload("evil.png", null))).isEqualTo(400);
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertThat(rejectedWith(empty)).isEqualTo(400);
    }

    @Test
    void clientFilenameNeverReachesDiskPath() throws Exception {
        new FileController(dir).uploadFile(upload("../../outside.png", "image/png"));

        // 文件只会出现在上传目录内，且名字不含客户端提供的任何部分。
        assertThat(dir.list()).hasSize(1).allMatch(name -> name.matches("[0-9a-f]{32}\\.png"));
        assertThat(new File(dir.getParentFile(), "outside.png")).doesNotExist();
    }

    @Test
    void storageFailureReturnsFixedMessageWithoutLeakingPath() throws Exception {
        // 把“上传目录”指向一个已存在的普通文件，保存必然失败。
        File notADirectory = new File(dir, "blocked");
        assertThat(notADirectory.createNewFile()).isTrue();
        FileController controller = new FileController(notADirectory);

        assertThatThrownBy(() -> controller.uploadFile(upload("a.png", "image/png")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(500);
                    assertThat(e.getMessage()).doesNotContain(dir.getAbsolutePath()).doesNotContain("blocked");
                });
    }
}
