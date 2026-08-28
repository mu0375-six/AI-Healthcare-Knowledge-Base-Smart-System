package com.healthkb.common;

import com.healthkb.rag.DocumentParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMagicTest {

    @Test
    void detectsAllSupportedSignatures() {
        assertEquals(FileMagic.PNG, FileMagic.detect(new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}));
        assertEquals(FileMagic.JPEG, FileMagic.detect(new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertEquals(FileMagic.GIF, FileMagic.detect("GIF89a".getBytes()));
        byte[] webp = new byte[16];
        webp[0] = 'R';
        webp[1] = 'I';
        webp[2] = 'F';
        webp[3] = 'F';
        webp[8] = 'W';
        webp[9] = 'E';
        webp[10] = 'B';
        webp[11] = 'P';
        assertEquals(FileMagic.WEBP, FileMagic.detect(webp));
        assertEquals(FileMagic.PDF, FileMagic.detect("%PDF-1.7 后续内容".getBytes()));
        assertEquals(FileMagic.ZIP, FileMagic.detect(new byte[]{
                'P', 'K', 0x03, 0x04, 0x14, 0x00}));
        assertEquals(FileMagic.OLE2, FileMagic.detect(new byte[]{
                (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}));
    }

    @Test
    void plainUtf8TextIsTextButBinaryIsNot() {
        assertEquals(FileMagic.TEXT, FileMagic.detect("空腹血糖 7.2 mmol/L\n正常文本，无 NUL 字节。".getBytes()));

        // Windows 可执行文件头（MZ + NUL）：既不是任何已知签名也不算文本
        assertEquals(FileMagic.UNKNOWN, FileMagic.detect(new byte[]{0x4D, 0x5A, 0x00, 0x00}));
        assertEquals(FileMagic.UNKNOWN, FileMagic.detect(new byte[]{1, 2}));
        assertEquals(FileMagic.UNKNOWN, FileMagic.detect(null));
    }

    @Test
    void extensionToTypeMappingRejectsSwappedSuffixes() {
        assertTrue(FileMagic.extMatches("pdf", FileMagic.PDF));
        assertTrue(FileMagic.extMatches("docx", FileMagic.ZIP));
        assertTrue(FileMagic.extMatches("doc", FileMagic.OLE2));
        assertTrue(FileMagic.extMatches("txt", FileMagic.TEXT));
        assertTrue(FileMagic.extMatches("jpg", FileMagic.JPEG));
        assertTrue(FileMagic.extMatches("jpeg", FileMagic.JPEG));

        assertFalse(FileMagic.extMatches("png", FileMagic.PDF), "伪装成 png 的 PDF 必须拦下");
        assertFalse(FileMagic.extMatches("pdf", FileMagic.UNKNOWN));
        assertFalse(FileMagic.extMatches("exe", FileMagic.TEXT));
    }

    @Test
    void documentParserRejectsFakeExtensionMismatch() {
        DocumentParser parser = new DocumentParser();

        var fake = new MockMultipartFile(
                "file", "trojan.png", "image/png", "%PDF-1.4 伪装成图片的文档".getBytes());
        AppException ex = assertThrows(AppException.class, () -> parser.validateUpload(fake));
        assertEquals(400, ex.getCode());

        var genuine = new MockMultipartFile(
                "file", "real.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0});
        parser.validateUpload(genuine); // 不抛即通过
    }
}
