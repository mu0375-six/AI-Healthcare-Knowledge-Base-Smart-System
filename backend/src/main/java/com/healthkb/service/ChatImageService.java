package com.healthkb.service;

import com.healthkb.common.AppException;
import com.healthkb.entity.ChatImage;
import com.healthkb.mapper.ChatImageMapper;
import com.healthkb.security.SecurityUtils;
import com.healthkb.rag.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatImageService {

    private static final Set<String> ALLOWED = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final long MAX_BYTES = 4L * 1024 * 1024;

    private final ChatImageMapper imageMapper;

    @Value("${app.chat.image-dir:./data/chat-images}")
    private String imageDir;

    public ChatImage upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("请选择图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw AppException.badRequest("单张图片不能超过 4MB");
        }
        String filename = file.getOriginalFilename() == null ? "image.jpg" : file.getOriginalFilename();
        String ext = extOf(filename);
        if (!ALLOWED.contains(ext)) {
            throw AppException.badRequest("只支持 png / jpg / jpeg / webp / gif");
        }
        String mime = mimeOf(ext, file.getContentType());
        String stored = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Path.of(imageDir);
        try {
            Files.createDirectories(dir);
            byte[] raw = file.getBytes();
            byte[] toWrite = maybeDownscale(raw, ext);
            Files.write(dir.resolve(stored), toWrite);
            ChatImage img = new ChatImage();
            img.setUserId(SecurityUtils.currentUserId());
            img.setFilename(filename);
            img.setMimeType(mime);
            img.setStoredName(stored);
            img.setByteSize((long) toWrite.length);
            img.setCreatedAt(LocalDateTime.now());
            imageMapper.insert(img);
            return img;
        } catch (IOException e) {
            throw AppException.badRequest("图片保存失败，请重试");
        }
    }

    public ChatImage requireOwned(Long id) {
        ChatImage img = imageMapper.selectById(id);
        if (img == null || !SecurityUtils.currentUserId().equals(img.getUserId())) {
            throw AppException.notFound("图片不存在");
        }
        return img;
    }

    public Path pathOf(ChatImage img) {
        return Path.of(imageDir).resolve(img.getStoredName());
    }

    public List<ChatImage> bindToMessage(List<Long> imageIds, Long sessionId, Long messageId) {
        if (imageIds == null || imageIds.isEmpty()) {
            return List.of();
        }
        if (imageIds.size() > 4) {
            throw AppException.badRequest("一次最多发送 4 张图片");
        }
        List<ChatImage> out = new ArrayList<>();
        for (Long id : imageIds) {
            if (id == null) {
                continue;
            }
            ChatImage img = requireOwned(id);
            img.setSessionId(sessionId);
            img.setMessageId(messageId);
            imageMapper.updateById(img);
            out.add(img);
        }
        return out;
    }

    public String describe(List<ChatImage> images) {
        if (images == null || images.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户在本轮发送了 ").append(images.size()).append(" 张图片。");
        sb.append("请结合可见/识别内容回答：检查单逐项说明，药品包装讲注意事项，皮肤或伤口只描述可见情况并提示就医，看不清要明说。\n");
        int i = 1;
        for (ChatImage img : images) {
            Path path = pathOf(img);
            String meta = metaLine(path, img);
            String ocr = ocr(path);
            if (ocr != null && !ocr.isBlank()) {
                img.setOcrText(ocr);
                imageMapper.updateById(img);
            }
            sb.append("\n图").append(i++).append("：").append(img.getFilename()).append(" ").append(meta).append("\n");
            if (ocr == null || ocr.isBlank()) {
                sb.append("未能从该图识别出文字。图片已随请求发送给多模态视觉模型，请直接观察图片内容作答；确实看不清时说明看不清。\n");
            } else {
                sb.append("从图中识别出的文字：\n").append(ocr.trim()).append("\n");
            }
        }
        return sb.toString();
    }

    public List<LlmClient.ImageInput> toVisionInputs(List<ChatImage> images) {
        List<LlmClient.ImageInput> out = new ArrayList<>();
        if (images == null || images.isEmpty()) {
            return out;
        }
        for (ChatImage img : images) {
            try {
                Path path = pathOf(img);
                if (path == null || !Files.isRegularFile(path)) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(path);
                String mime = img.getMimeType() == null ? "image/jpeg" : img.getMimeType();
                out.add(new LlmClient.ImageInput(Base64.getEncoder().encodeToString(bytes), mime));
            } catch (IOException e) {
                log.warn("读取图片 {} 失败: {}", img.getId(), e.toString());
            }
        }
        return out;
    }

    public List<Map<String, Object>> toAttachments(List<ChatImage> images) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChatImage img : images) {
            list.add(Map.of(
                    "id", img.getId(),
                    "filename", img.getFilename() == null ? "image" : img.getFilename(),
                    "mimeType", img.getMimeType() == null ? "image/jpeg" : img.getMimeType()
            ));
        }
        return list;
    }

    private String ocr(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return "";
        }
        String text = runTesseract(path, "chi_sim+eng");
        if (text.isBlank()) {
            text = runTesseract(path, "eng");
        }
        return text.length() > 4000 ? text.substring(0, 4000) + "…" : text;
    }

    private String runTesseract(Path path, String lang) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    tesseractCmd(), path.toAbsolutePath().toString(), "stdout", "-l", lang, "--psm", "6");
            Path localTess = Path.of(System.getProperty("user.dir"), "data", "tessdata");
            if (Files.isDirectory(localTess) && Files.isRegularFile(localTess.resolve("chi_sim.traineddata"))) {
                pb.environment().put("TESSDATA_PREFIX", localTess.toAbsolutePath().toString());
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(20, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return "";
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.exitValue() != 0 && out.toLowerCase(Locale.ROOT).contains("error")) {
                return "";
            }
            return out.replaceAll("(?i)tesseract.*\\n", "").trim();
        } catch (Exception e) {
            log.debug("tesseract 不可用: {}", e.toString());
            return "";
        }
    }

    private String metaLine(Path path, ChatImage img) {
        try (InputStream in = Files.newInputStream(path)) {
            BufferedImage bi = ImageIO.read(in);
            if (bi != null) {
                return "（" + bi.getWidth() + "×" + bi.getHeight() + "，" + img.getByteSize() + " 字节）";
            }
        } catch (Exception ignored) {
        }
        return "（" + (img.getByteSize() == null ? 0 : img.getByteSize()) + " 字节）";
    }

    private byte[] maybeDownscale(byte[] raw, String ext) {
        if (!Set.of("png", "jpg", "jpeg").contains(ext)) {
            return raw;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
            if (src == null) {
                return raw;
            }
            int max = 1600;
            int w = src.getWidth();
            int h = src.getHeight();
            if (w <= max && h <= max) {
                return raw;
            }
            double scale = Math.min(max / (double) w, max / (double) h);
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));
            BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            ImageIO.write(dst, "jpg".equals(ext) || "jpeg".equals(ext) ? "jpg" : "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return raw;
        }
    }

    private static String tesseractCmd() {
        String[] candidates = {
                "C:\\Program Files\\Tesseract-OCR\\tesseract.exe",
                "C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe",
                "tesseract"
        };
        for (String c : candidates) {
            if (!"tesseract".equals(c) && Files.isRegularFile(Path.of(c))) {
                return c;
            }
        }
        return "tesseract";
    }

    private static String extOf(String filename) {
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static String mimeOf(String ext, String hinted) {
        return switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> hinted != null && hinted.startsWith("image/") ? hinted : "image/jpeg";
        };
    }
}
