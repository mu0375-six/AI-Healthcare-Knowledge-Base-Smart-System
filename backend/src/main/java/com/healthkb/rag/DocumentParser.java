package com.healthkb.rag;

import com.healthkb.common.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
public class DocumentParser {

    public static final Set<String> ALLOWED_EXT = Set.of("pdf", "doc", "docx", "txt", "png", "jpg", "jpeg");
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private final Tika tika = new Tika();

    public String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("请选择要上传的文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw AppException.badRequest("文件大小不能超过 10MB");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw AppException.badRequest("不支持的文件类型，仅允许 pdf/doc/docx/txt/png/jpg/jpeg");
        }
    }

    public String extractText(MultipartFile file, String extractedText) {
        validateUpload(file);
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extensionOf(filename);
        boolean image = Set.of("png", "jpg", "jpeg").contains(ext);
        if (extractedText != null && !extractedText.isBlank()) {
            return extractedText.trim();
        }
        if (image) {
            if (filename.toLowerCase(Locale.ROOT).contains("demo")) {
                return demoImageText(filename);
            }
            return "";
        }
        try {
            String text = tika.parseToString(file.getInputStream());
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            log.warn("文档解析失败: {}", filename);
            throw AppException.badRequest("无法解析该文件，请改用 txt/Word/PDF 或粘贴文本");
        }
    }

    private String demoImageText(String filename) {
        return """
                演示体检报告（由文件名含 demo 触发的内置解析）
                文件：%s
                空腹血糖 7.2 mmol/L (3.9-6.1)
                收缩压 148 mmHg (90-139)
                舒张压 92 mmHg (60-89)
                总胆固醇 5.8 mmol/L (0-5.2)
                甘油三酯 2.1 mmol/L (0-1.7)
                """.formatted(filename);
    }
}
