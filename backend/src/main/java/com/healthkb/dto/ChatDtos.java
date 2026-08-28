package com.healthkb.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

public class ChatDtos {

    @Data
    public static class CreateSessionRequest {
        private String title;
    }

    @Data
    public static class RenameSessionRequest {
        @Size(min = 1, max = 60, message = "会话名称长度为 1-60 字")
        private String title;
    }

    @Data
    public static class AskRequest {
        private Long sessionId;
        /** 问题长度上限：超长文本会直通 embedding 与大模型，限流只挡次数不挡体积。 */
        @Size(max = 500, message = "问题描述不能超过 500 字")
        private String question;
        @Size(max = 4, message = "一次最多发送 4 张图片")
        private java.util.List<Long> imageIds;
        private Long profileId;
    }
}
