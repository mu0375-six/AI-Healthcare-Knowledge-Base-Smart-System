package com.healthkb.common;

import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 按文件头字节识别真实类型 —— 扩展名可以被随手改名（还能被直接当攻击载荷），
 * 内容签名不会。上传校验先看扩展名白名单，再用这里做「内容与扩展名一致」的二次确认，
 * 同时图片的真实 MIME 也以此推导，不再信任客户端声明的 Content-Type。
 */
public enum FileMagic {

    PNG, JPEG, GIF, WEBP, PDF, ZIP, OLE2, TEXT, UNKNOWN;

    /**
     * @param head 文件头部字节（建议至少 16 字节；TEXT 判定最多看前 8KB 更稳）
     */
    public static FileMagic detect(byte[] head) {
        if (head == null || head.length < 4) {
            return UNKNOWN;
        }
        if (matches(head, 0x89, 'P', 'N', 'G')) {
            return PNG; // 89 50 4E 47 0D 0A 1A 0A
        }
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (startsWith(head, "GIF87a") || startsWith(head, "GIF89a")) {
            return GIF;
        }
        if (startsWith(head, "RIFF") && head.length >= 12 && startsWithAt(head, 8, "WEBP")) {
            return WEBP;
        }
        if (startsWith(head, "%PDF")) {
            return PDF;
        }
        if (startsWith(head, "PK\u0003\u0004") || startsWith(head, "PK\u0005\u0006")
                || startsWith(head, "PK\u0007\u0008")) {
            return ZIP; // docx / pptx / xlsx 的容器格式
        }
        if (matches(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return OLE2; // 老版 .doc
        }
        return looksLikeText(head) ? TEXT : UNKNOWN;
    }

    /** 扩展名是否与检出类型相容。 */
    public static boolean extMatches(String ext, FileMagic type) {
        String e = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        return switch (type) {
            case PDF -> e.equals("pdf");
            case ZIP -> e.equals("docx");
            case OLE2 -> e.equals("doc");
            case TEXT -> e.equals("txt");
            case PNG -> e.equals("png");
            case JPEG -> e.equals("jpg") || e.equals("jpeg");
            case GIF -> e.equals("gif");
            case WEBP -> e.equals("webp");
            case UNKNOWN -> false;
        };
    }

    /** 检出类型的规范 MIME；未知返回 null。 */
    public static String mimeOf(FileMagic type) {
        return switch (type) {
            case PNG -> "image/png";
            case JPEG -> "image/jpeg";
            case GIF -> "image/gif";
            case WEBP -> "image/webp";
            default -> null;
        };
    }

    /**
     * 文本判定：前 8KB 内不含 NUL 字节且整段可按 UTF-8 无损解码。
     * 二进制可执行文件几乎必然含 NUL，误报面极小；代价是 UTF-16 编码的 txt 会被拒，
     * 对本系统可接受（Tika 解析链路与种子文本均为 UTF-8）。
     */
    private static boolean looksLikeText(byte[] data) {
        for (byte b : data) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(data));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean matches(byte[] head, int... expected) {
        if (head.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] head, String prefix) {
        return startsWithAt(head, 0, prefix);
    }

    private static boolean startsWithAt(byte[] head, int offset, String prefix) {
        if (head.length - offset < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (head[offset + i] != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** 图片扩展名集合，供调用方做前置判断。 */
    public static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "webp", "gif");
}
