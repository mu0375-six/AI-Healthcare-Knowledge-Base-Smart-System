package com.healthkb.rag;

import com.healthkb.common.AppException;
import com.healthkb.common.FileMagic;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
public class DocumentParser {

    public static final Set<String> ALLOWED_EXT = Set.of("pdf", "doc", "docx", "txt", "png", "jpg", "jpeg");
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    /** Tika parseToString 默认 10 万字符静默截断，超长报告后半部分无声丢失；这里显式放宽并留痕。 */
    private static final int MAX_TEXT_CHARS = 200_000;

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
        requireMagicMatches(ext, file);
    }

    /** 扩展名可被随手改，签名不会：内容与扩展名不符一律按伪装文件拒绝。 */
    private static void requireMagicMatches(String ext, MultipartFile file) {
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(8 * 1024);
        } catch (Exception e) {
            throw AppException.badRequest("无法读取上传文件，请重试");
        }
        FileMagic type = FileMagic.detect(head);
        if (!FileMagic.extMatches(ext, type)) {
            throw AppException.badRequest("文件内容与扩展名不符，请勿修改后缀后上传");
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
            // 图片无法本地 OCR，文本识别交给多模态模型（ReportService 调 LLM）；
            // 知识库上传场景图片本身不参与向量化，返回空串即可。
            return "";
        }
        try {
            String text = tika.parseToString(file.getInputStream(),
                    new org.apache.tika.metadata.Metadata(), MAX_TEXT_CHARS);
            if (text != null && text.length() >= MAX_TEXT_CHARS) {
                log.warn("文档「{}」超过 {} 字符上限，解析结果已截断", filename, MAX_TEXT_CHARS);
            }
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            log.warn("文档解析失败: {}", filename);
            throw AppException.badRequest("无法解析该文件，请改用 txt/Word/PDF 或粘贴文本");
        }
    }

}
