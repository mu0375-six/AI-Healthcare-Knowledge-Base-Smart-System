package com.healthkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_image")
public class ChatImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long sessionId;
    private Long messageId;
    private String filename;
    private String mimeType;
    private String storedName;
    private Long byteSize;
    private String ocrText;
    private LocalDateTime createdAt;
}
