package com.healthkb.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class KnowledgeDtos {

    @Data
    public static class TextRequest {
        @NotBlank(message = "标题不能为空")
        private String title;
        private String category;
        @NotBlank(message = "正文不能为空")
        private String content;
        private String source;
    }
}
