package com.healthkb.dto;

import lombok.Data;

public class NewsDtos {

    /** 列表卡片：不带正文，image 表示是否值得请求 /news/{id}/image。 */
    @Data
    public static class ListItem {
        private Long id;
        private String title;
        private String summary;
        private String sourceName;
        private String category;
        private String publishedOn;
        private boolean image;
    }

    @Data
    public static class Detail {
        private Long id;
        private String title;
        private String summary;
        private String content;
        private String sourceName;
        private String sourceUrl;
        private String category;
        private String publishedOn;
        private boolean image;
    }
}
