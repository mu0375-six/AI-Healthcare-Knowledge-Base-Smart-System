package com.healthkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_report")
public class ExamReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long profileId;
    private String filename;
    private String rawText;
    private String summary;
    private LocalDateTime createdAt;
}
