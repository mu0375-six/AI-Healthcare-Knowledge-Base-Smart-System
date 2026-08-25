package com.healthkb.dto;

import lombok.Data;

public class ChatDtos {

    @Data
    public static class CreateSessionRequest {
        private String title;
    }

    @Data
    public static class AskRequest {
        private Long sessionId;
        private String question;
        private java.util.List<Long> imageIds;
        private Long profileId;
    }
}
