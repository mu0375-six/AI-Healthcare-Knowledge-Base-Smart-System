package com.healthkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 首页健康新闻。常规条目来自启动/定时爬取的权威源（世界卫生组织中文新闻室），
 * 断网时落库的是内置科普快照（builtinImage 非空、sourceName 注明出处）。
 */
@Data
@TableName("news_item")
public class NewsItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String summary;
    /** 段落之间以空行分隔的纯文本，前端按空行切段渲染。 */
    private String content;
    /** 下载到本地 data/news-images/ 下的图片文件名。 */
    private String imageName;
    /** 内置插画名（resources/news-images/<name>.svg），兜底科普条目用。 */
    private String builtinImage;
    private String sourceName;
    private String sourceUrl;
    private String category;
    private LocalDate publishedOn;
    private LocalDateTime crawledAt;
}
