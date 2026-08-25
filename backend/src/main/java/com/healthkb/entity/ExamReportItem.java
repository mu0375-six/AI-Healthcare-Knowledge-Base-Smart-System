package com.healthkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@TableName("exam_report_item")
public class ExamReportItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private String name;
    @TableField("item_value")
    @JsonProperty("value")
    private String itemValue;
    private String unit;
    private String refRange;
    private String flag;
    private String interpretation;
}
