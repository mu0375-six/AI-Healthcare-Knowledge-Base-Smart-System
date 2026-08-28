package com.healthkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户主动保存的就医位置（导诊「附近医疗资源」用）。
 * 一人一条：重复保存覆盖；坐标只在用户勾选「保存此地址」时落库。
 */
@Data
@TableName("user_location")
public class UserLocation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String addressText;
    private Double longitude;
    private Double latitude;
    private LocalDateTime savedAt;
}
