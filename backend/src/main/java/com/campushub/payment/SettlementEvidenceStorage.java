package com.campushub.payment;

import com.campushub.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** 结算凭证包含隐私，存到静态 uploads 映射之外，仅经管理员鉴权接口下载。 */
@Component
public class SettlementEvidenceStorage {
    private final Path directory;
    public SettlementEvidenceStorage(@Value("${campushub.payment.evidence-directory:private-storage/settlements}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new BusinessException(400, "凭证须为5MB以内的PNG、JPEG或PDF文件");
        try {
            byte[] bytes = file.getBytes();
            String extension;
            // 检查文件头而非信任用户提供的扩展名；下载始终使用附件，禁止作为网页执行。
            if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') extension = ".png";
            else if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) extension = ".jpg";
            else if (bytes.length >= 5 && new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) extension = ".pdf";
            else throw new BusinessException(400, "凭证文件格式不支持");
            Files.createDirectories(directory);
            String filename = UUID.randomUUID().toString() + extension;
            Files.write(directory.resolve(filename), bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
            return filename;
        } catch (IOException ex) { throw new BusinessException(500, "凭证保存失败，请稍后重试"); }
    }
    public Resource read(String filename) {
        Path path = directory.resolve(filename).normalize();
        if (!path.getParent().equals(directory) || !Files.isRegularFile(path)) throw new BusinessException(404, "凭证不存在");
        try { return new UrlResource(path.toUri()); }
        catch (java.net.MalformedURLException ex) { throw new BusinessException(404, "凭证不存在"); }
    }
    public void deleteAfterRollback(String filename) {
        try { Files.deleteIfExists(directory.resolve(filename)); }
        catch (IOException ignored) { /* 仅清理未提交凭证；保留孤立文件比误删财务证据更安全。 */ }
    }
}
